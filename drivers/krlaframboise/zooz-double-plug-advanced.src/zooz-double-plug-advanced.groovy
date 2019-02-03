/**
 *  Zooz Double Plug Advanced v1.1
 *  (Models: ZEN25)
 *
 *  Author: 
 *    Kevin LaFramboise (krlaframboise)
 *
 *	Documentation:
 *
 *  Changelog:
 *
 *    1.1 (02/03/2019)
 *      - Fixed power reporting threshold options
 *
 *    1.0 (12/21/2018)
 *      - Initial Release
 *
 *
 *  Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License. You may obtain a copy of the License at:
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
 *  for the specific language governing permissions and limitations under the License.
 *
 */
metadata {
	definition (
		name: "Zooz Double Plug Advanced", 
		namespace: "krlaframboise", 
		author: "Kevin LaFramboise",
		vid:"generic-switch-power-energy"
	) {
		capability "Actuator"
		capability "Sensor"
		capability "Switch"		
		capability "Outlet"
		capability "Power Meter"
		capability "Voltage Measurement"
		capability "Energy Meter"
		capability "Configuration"
		capability "Refresh"
		
		attribute "firmwareVersion", "string"		
		attribute "syncStatus", "string"
		attribute "lastCheckin", "string"
		attribute "energyTime", "number"
		attribute "current", "number"
		attribute "energyDuration", "string"
		
		["power", "voltage", "current"].each {
			attribute "${it}Low", "number"
			attribute "${it}High", "number"
		}
		
		command "reset"

		fingerprint deviceId: "A003", manufacturer: "027A", prod: "A000", inClusters:"0x5E,0x25,0x85,0x8E,0x59,0x55,0x86,0x72,0x5A,0x73,0x70,0x71,0x32,0x9F,0x60,0x6C,0x7A", deviceJoinName: "Zooz Double Plug"
	}

	preferences {
		configParams.each {
			getOptionsInput(it)
		}
	
		getBoolInput("debugOutput", "Enable Debug Logging", true)
	}
}


private getOptionsInput(param) {
	input "configParam${param.num}", "enum",
		title: "${param.name}:",
		required: false,
		defaultValue: "${param.value}",
		displayDuringSetup: true,
		options: param.options
}

private getBoolInput(name, title, defaultVal) {
	input "${name}", "bool", 
		title: "${title}?", 
		defaultValue: defaultVal, 
		required: false
}

def installed () { 
	sendEvent(name:"energyTime", value:new Date().time, displayed: false)
	
	// Make sure the outlets get created if using the new mobile app.
	runIn(5, createChildDevices) 
}

def updated() {		
	unschedule()
	
	runEvery3Hours(ping)
	
	def cmds = []
	
	if (childDevices?.size() != 3) {
		cmds += createChildDevices()
	}
	
	cmds += configure()
	return cmds 		
}


def createChildDevices() {
	def cmds = []
	
	(1..2).each { endPoint ->
		if (!findChildByEndPoint(endPoint)) {			
			def dni = "${getChildDeviceNetworkId(endPoint)}"
			
			addChildOutlet(dni, endPoint)
						
			cmds += childReset(dni)
		}
	}
	
	def dni = "${getChildDeviceNetworkId(3)}"
	if (!findChildByDeviceNetworkId(dni)) {	
		addChildUSB(dni)
		cmds << switchBinaryGetCmd(3)
	}
	return cmds ? delayBetween(cmds, 1000) : []
}

private addChildOutlet(dni, endPoint) {
	def name = getEndPointName(endPoint)?.toUpperCase()
	
	logDebug "Creating ${name} Outlet Child Device"	
	
	addChildDevice(
		"krlaframboise", 
		"Zooz Double Plug Outlet Advanced", 
		dni, 
		[
			isComponent: true,
			label: "${device.displayName}-${name} Outlet"
		]
	)
}
	
private addChildUSB(dni) {
	logDebug "Creating USB Child Device"
	addChildDevice(
		"hubitat",
		"Virtual Switch",
		dni, 
		[
			isComponent: true,
			label: "${device.displayName}-USB (READ-ONLY)"
		]
	)
}


def configure() {	
	runIn(10, updateSyncStatus)
			
	def cmds = []
	
	if (!device.currentValue("firmwareVersion")) {
		cmds << versionGetCmd()
		cmds << "delay 250"
	}
	
	if (device.currentValue("power") == null) {
		cmds += getRefreshCmds()
		cmds << "delay 250"
	}
	
	if (device.currentValue("energy") == null) {
		cmds += getResetCmds()
		cmds << "delay 250"
	}
	
	if (device.currentValue("switch")) {
		cmds += getConfigureCmds()
	}
	else {
		configParams.each {		
			cmds << configGetCmd(it)
			cmds << "delay 250"
		}
	}	
	return cmds
}


private getConfigureCmds() {
	def cmds = []	
	
	if (state.resyncAll) {
		cmds << versionGetCmd()
	}

	configParams.each { 
		def storedVal = getParamStoredValue(it.num)
		if (state.resyncAll || "${storedVal}" != "${it.value}") {
			if (state.configured) {
				logDebug "CHANGING ${it.name}(#${it.num}) from ${storedVal} to ${it.value}"
				cmds << configSetCmd(it)
			}
			cmds << configGetCmd(it)
		}
	}
	return cmds ? delayBetween(cmds, 250) : []
}


def ping() {
	logDebug "ping()..."
	return sendCmds([basicGetCmd()])
}


def on() {
	logDebug "on()..."
	return getChildSwitchCmds(0xFF, null)
}

def off() {
	logDebug "off()..."
	return getChildSwitchCmds(0x00, null)
}


def childOn(dni) {
	logDebug "childOn(${dni})..."
	sendCmds(getChildSwitchCmds(0xFF, dni))
}

def childOff(dni) {
	logDebug "childOff(${dni})..."
	sendCmds(getChildSwitchCmds(0x00, dni))
}

private getChildSwitchCmds(value, dni) {
	def endPoint = getEndPoint(dni)	
	log.warn "$endPoint"
	return delayBetween([
		switchBinarySetCmd(value, endPoint),
		switchBinaryGetCmd(endPoint)
	], 500)
}


def refresh() {
	logDebug "refresh()..."
	def cmds = getRefreshCmds()
	
	childDevices.each {
		def dni = it.deviceNetworkId
		def endPoint = getEndPoint(dni)
		def endPointName = getEndPointName(endPoint)		
		
		cmds << "delay 250"
		if (endPointName == "usb") {
			cmds << switchBinaryGetCmd(endPoint)
		}
		else {
			cmds += getRefreshCmds(dni)			
		}
	}
	return cmds	
}

def childRefresh(dni) {
	logDebug "childRefresh($dni)..."
	sendCmds(getRefreshCmds(dni))
}

private getRefreshCmds(dni=null) {
	def endPoint = getEndPoint(dni)
	delayBetween([ 
		switchBinaryGetCmd(endPoint),
		meterGetCmd(meterEnergy, endPoint),
		meterGetCmd(meterPower, endPoint),
		meterGetCmd(meterVoltage, endPoint),
		meterGetCmd(meterCurrent, endPoint)
	], 1000)
}


def reset() {
	logDebug "reset()..."
	
	runIn(10, refresh)
	
	def cmds = getResetCmds()	
	childDevices.each { child ->		
		if (child.hasCommand("reset")) {
			cmds << "delay 1000"
			cmds += getResetCmds(child.deviceNetworkId)
		}
	}
	return cmds
}

def childReset(dni) {
	logDebug "childReset($dni)"
	
	def cmds = getResetCmds(dni)
	cmds << "delay 1000"
	cmds += getRefreshCmds(dni)
	sendCmds(cmds)
}

private getResetCmds(dni=null) {
	def endPoint = getEndPoint(dni)
	def child = findChildByDeviceNetworkId(dni)
		
	["power", "voltage", "current"].each {
		executeSendEvent(child, createEventMap("${it}Low", safeToDec(getAttrVal(it),0), false))
		executeSendEvent(child, createEventMap("${it}High", safeToDec(getAttrVal(it),0), false))
	}
	executeSendEvent(child, createEventMap("energyTime", new Date().time, false))
	
	return [meterResetCmd(endPoint)]
}


private sendCmds(cmds) {
	runIn(0, executeCmds,[data:[cmds:cmds]])
	return []
}

def executeCmds(data) {
	return data.cmds
}


private versionGetCmd() {
	return secureCmd(zwave.versionV1.versionGet())
}

private basicGetCmd() {
	return secureCmd(zwave.basicV1.basicGet())
}

private meterGetCmd(meter, endPoint) {
	return multiChannelCmdEncapCmd(zwave.meterV3.meterGet(scale: meter.scale), endPoint)
}

private meterResetCmd(endPoint) {
	return multiChannelCmdEncapCmd(zwave.meterV3.meterReset(), endPoint)
}

private switchBinaryGetCmd(endPoint) {
	return multiChannelCmdEncapCmd(zwave.switchBinaryV1.switchBinaryGet(), endPoint)
}

private switchBinarySetCmd(val, endPoint) {
	return multiChannelCmdEncapCmd(zwave.switchBinaryV1.switchBinarySet(switchValue: val), endPoint)
}

private multiChannelCmdEncapCmd(cmd, endPoint) {	
	return secureCmd(zwave.multiChannelV3.multiChannelCmdEncap(destinationEndPoint:safeToInt(endPoint)).encapsulate(cmd))
}

private configSetCmd(param) {
	return secureCmd(zwave.configurationV1.configurationSet(parameterNumber: param.num, size: param.size, scaledConfigurationValue: param.value))
}

private configGetCmd(param) {
	return secureCmd(zwave.configurationV2.configurationGet(parameterNumber: param.num))
}

private secureCmd(cmd) {
	if (getDataValue("zwaveSecurePairingComplete") == "true") {
		return zwave.securityV1.securityMessageEncapsulation().encapsulate(cmd).format()
	}
	else {
		return cmd.format()
	}	
}


private getCommandClassVersions() {
	[
		0x20: 1,	// Basic
		0x25: 1,	// Switch Binary
		0x32: 3,	// Meter v4
		0x59: 1,	// AssociationGrpInfo
		0x55: 1,	// Transport Service
		0x5A: 1,	// DeviceResetLocally
		0x5E: 2,	// ZwaveplusInfo
		0x60: 3,	// Multi Channel (4)
		0x6C: 1,	// Supervision
		0x70: 2,	// Configuration
		0x71: 3,	// Notification
		0x72: 2,	// ManufacturerSpecific
		0x73: 1,	// Powerlevel
		0x7A: 2,	// Firmware Update Md (3)
		0x85: 2,	// Association
		0x86: 1,	// Version (2)
		0x8E: 2,	// Multi Channel Association
		0x98: 1,	// Security 0
		0x9F: 1		// Security 2
	]
}


def parse(String description) {	
	def result = []
	try {
		if (!"${description}".contains("command: 5E02")) {
			def cmd = zwave.parse(description, commandClassVersions)
			if (cmd) {
				result += zwaveEvent(cmd)		
			}
			else {
				log.warn "Unable to parse: $description"
			}
		}
			
		if (!isDuplicateCommand(state.lastCheckinTime, 60000)) {
			state.lastCheckinTime = new Date().time
			sendEvent(createEventMap("lastCheckin", convertToLocalTimeString(new Date()), false))
		}
	}
	catch (e) {
		log.error "${e}"
	}
	return result
}


def zwaveEvent(hubitat.zwave.commands.securityv1.SecurityMessageEncapsulation cmd) {
	def encapsulatedCmd = cmd.encapsulatedCommand(commandClassVersions)	
	
	def result = []
	if (encapsulatedCmd) {
		result += zwaveEvent(encapsulatedCmd)
	}
	else {
		log.warn "Unable to extract encapsulated cmd from $cmd"
	}
	return result
}


def zwaveEvent(hubitat.zwave.commands.multichannelv3.MultiChannelCmdEncap cmd) {
	def encapsulatedCommand = cmd.encapsulatedCommand([0x31: 3])
	
	if (encapsulatedCommand) {
		return zwaveEvent(encapsulatedCommand, cmd.sourceEndPoint)
	}
	else {
		logDebug "Unable to get encapsulated command: $cmd"
		return []
	}
}


def zwaveEvent(hubitat.zwave.commands.versionv1.VersionReport cmd) {
	logTrace "VersionReport: ${cmd}"
	
	def version = "${cmd.applicationVersion}.${cmd.applicationSubVersion}"
	
	if (version != device.currentValue("firmwareVersion")) {
		logDebug "Firmware: ${version}"
		sendEvent(name: "firmwareVersion", value: version, displayed:false)
	}
	return []	
}


def zwaveEvent(hubitat.zwave.commands.configurationv2.ConfigurationReport cmd) {	
	state.configured = true
	
	updateSyncStatus("Syncing...")
	runIn(10, updateSyncStatus)
	
	def param = configParams.find { it.num == cmd.parameterNumber }
	if (param) {	
		def val = cmd.size == 1 ? cmd.configurationValue[0] : cmd.scaledConfigurationValue
		
		logDebug "${param.name}(#${param.num}) = ${val}"
		setParamStoredValue(param.num, val)				
	}
	else {
		logDebug "Unknown Parameter #${cmd.parameterNumber} = ${val}"
	}		
	state.resyncAll = false	
	return []
}

def updateSyncStatus(status=null) {	
	if (status == null) {	
		def changes = getPendingChanges()
		if (changes > 0) {
			status = "${changes} Pending Change" + ((changes > 1) ? "s" : "")
		}
		else {
			status = "Synced"
		}
	}	
	if ("${syncStatus}" != "${status}") {
		executeSendEvent(null, createEventMap("syncStatus", status, false))		
	}
}

private getSyncStatus() {
	return device.currentValue("syncStatus")
}

private getPendingChanges() {
	return (configParams.count { isConfigParamSynced(it) ? 0 : 1 })
}

private isConfigParamSynced(param) {
	return (param.value == getParamStoredValue(param.num))
}

private getParamStoredValue(paramNum) {
	return safeToInt(state["configVal${paramNum}"], null)
}

private setParamStoredValue(paramNum, value) {
	state["configVal${paramNum}"] = value
}


def zwaveEvent(hubitat.zwave.commands.switchbinaryv1.SwitchBinaryReport cmd, endPoint=0) {
	logTrace "SwitchBinaryReport: ${cmd} (${getEndPointName(endPoint)})"
	
	def value = (cmd.value == 0xFF) ? "on" : "off"
	
	executeSendEvent(findChildByEndPoint(endPoint), createEventMap("switch", value))
	return []
}


def zwaveEvent(hubitat.zwave.commands.basicv1.BasicReport cmd, endPoint=0) {
	logTrace "BasicReport: ${cmd} (${getEndPointName(endPoint)})"
	
	return []
}


def zwaveEvent(hubitat.zwave.commands.meterv3.MeterReport cmd, endPoint=0) {
	def val = roundTwoPlaces(cmd.scaledMeterValue)
	def child = findChildByEndPoint(endPoint)	
	
	switch (cmd.scale) {
		case meterEnergy.scale:			
			sendEnergyEvents(child, val)
			break
		case meterPower.scale:
			sendMeterEvents(child, meterPower, val)			
			break
		case meterVoltage.scale:
			sendMeterEvents(child, meterVoltage, val)
			break
		case meterCurrent.scale:
			sendMeterEvents(child, meterCurrent, val)
			break
		default:
			logDebug "Unknown Meter Scale: $cmd"
	}
	
	return []
}

private sendMeterEvents(child, meter, value) {
	def highLowNames = [] 
	
	executeSendEvent(child, createEventMap(meter.name, value, meter.displayed, meter.unit))
	
	def highName = "${meter.name}High"
	if (getAttrVal(highName, child) == null || value > getAttrVal(highName, child)) {
		highLowNames << highName
	}

	def lowName = "${meter.name}Low"
	if (getAttrVal(lowName, child) == null || value < getAttrVal(lowName, child)) {
		highLowNames << lowName
	}
	
	highLowNames.each {
		executeSendEvent(child, createEventMap("$it", value, false, meterPower.unit))
	}	
}



private sendEnergyEvents(child, value) {
	executeSendEvent(child, createEventMap("energy", value, meterEnergy.displayed, meterEnergy.unit))
	
	executeSendEvent(child, createEventMap("energyDuration", calculateEnergyDuration(child), false))
}

private calculateEnergyDuration(child) {
	def energyTimeMS = getAttrVal("energyTime", child)
	if (!energyTimeMS) {
		return "Unknown"
	}
	else {
		def duration = roundTwoPlaces((new Date().time - energyTimeMS) / 60000)
		
		if (duration >= (24 * 60)) {
			return getFormattedDuration(duration, (24 * 60), "Day")
		}
		else if (duration >= 60) {
			return getFormattedDuration(duration, 60, "Hour")
		}
		else {
			return getFormattedDuration(duration, 0, "Minute")
		}
	}
}

private getFormattedDuration(duration, divisor, name) {
	if (divisor) {
		duration = roundTwoPlaces(duration / divisor)
	}	
	return "${duration} ${name}${duration == 1 ? '' : 's'}"
}


def zwaveEvent(hubitat.zwave.Command cmd) {
	logDebug "Unhandled zwaveEvent: $cmd"
	return []
}


// Meters
private getMeterEnergy() { 
	return getMeterMap("energy", 0, "kWh", null, settings?.displayEnergy != false) 
}

private getMeterPower() { 
	return getMeterMap("power", 2, "W", 2000, settings?.displayPower != false)
}

private getMeterVoltage() { 
	return getMeterMap("voltage", 4, "V", 150, settings?.displayVoltage != false) 
}

private getMeterCurrent() { 
	return getMeterMap("current", 5, "A", 18, settings?.displayCurrent != false)
}

private getMeterMap(name, scale, unit, limit, displayed) {
	return [name:name, scale:scale, unit:unit, limit: limit, displayed:displayed]
}



// Configuration Parameters
private getConfigParams() {
	return [
		powerFailureRecoveryParam,
		overloadProtectionParam,
		manualControlParam,
		ledIndicatorModeParam,
		powerReportingThresholdParam,
		powerReportingFrequencyParam,
		energyReportingFrequencyParam,
		voltageReportingFrequencyParam,
		ampsReportingFrequencyParam,		
		leftAutoOffEnabledParam,
		leftAutoOffIntervalParam,
		rightAutoOffEnabledParam,
		rightAutoOffIntervalParam,
		leftAutoOnEnabledParam,
		leftAutoOnIntervalParam,
		rightAutoOnEnabledParam,
		rightAutoOnIntervalParam
	]
}

private getPowerFailureRecoveryParam() {
	return getParam(1, "On/Off Status Recovery After Power Failure", 1, 0, [0:"Restore Outlets States From Before Power Faiure", 1:"Turn Outlets On", 2:"Turn Outlets Off"])
}

private getPowerReportingThresholdParam() {
	return getParam(2, "Power Reporting Threshold", 4, 5, powerReportingThresholdOptions) 
}

private getPowerReportingFrequencyParam() {
	return getParam(3, "Power Reporting Frequency", 4, 30, frequencyOptions)
}

private getEnergyReportingFrequencyParam() {
	return getParam(4, "Energy Reporting Frequency", 4, 300, frequencyOptions) 
}

private getVoltageReportingFrequencyParam() {
	return getParam(5, "Voltage Reporting Frequency", 4, 300, frequencyOptions) 
}

private getAmpsReportingFrequencyParam() {
	return getParam(6, "Electrical Current Reporting Frequency", 4, 300, frequencyOptions) 
}

private getOverloadProtectionParam() {
	return getParam(7, "Overload Protection", 1, 10, overloadOptions) 
}

private getLeftAutoOffEnabledParam() {
	return getParam(8, "Left Outlet Auto Turn-Off Timer Enabled", 1, 0, enabledOptions)
}

private getLeftAutoOffIntervalParam() {
	return getParam(9, "Left Outlet Auto Turn-Off After", 4, 60, autoOnOffIntervalOptions)
}

private getRightAutoOffEnabledParam() {
	return getParam(12, "Right Outlet Auto Turn-Off Timer Enabled", 1, 0, enabledOptions)
}

private getRightAutoOffIntervalParam() {
	return getParam(13, "Right Outlet Auto Turn-Off After", 4, 60, autoOnOffIntervalOptions)
}

private getLeftAutoOnEnabledParam() {
	return getParam(10, "Left Outlet Auto Turn-On Timer Enabled", 1, 0, enabledOptions)
}

private getLeftAutoOnIntervalParam() {
	return getParam(11, "Left Outlet Auto Turn-On After", 4, 60, autoOnOffIntervalOptions)
}

private getRightAutoOnEnabledParam() {
	return getParam(14, "Right Outlet Auto Turn-On Timer Enabled", 1, 0, enabledOptions)
}

private getRightAutoOnIntervalParam() {
	return getParam(15, "Right Outlet Auto Turn-On After", 4, 60, autoOnOffIntervalOptions)
}

private getManualControlParam() {
	return getParam(16, "Manual Control", 1, 1, enabledOptions)
}

private getLedIndicatorModeParam() {
	return getParam(17, "LED Indicator Mode", 1, 1, [0:"Always On", 1:"On When Switch On", 2:"LED On for 5 Seconds", 3:"LED Always Off"])
}

private getParam(num, name, size, defaultVal, options=null) {
	def val = safeToInt((settings ? settings["configParam${num}"] : null), defaultVal) 
	
	def map = [num: num, name: name, size: size, value: val]
	if (options) {
		map.valueName = options?.find { k, v -> "${k}" == "${val}" }?.value
		map.options = setDefaultOption(options, defaultVal)
	}
	
	return map
}

private setDefaultOption(options, defaultVal) {
	return options?.collect { k, v ->
		if ("${k}" == "${defaultVal}") {
			v = "${v} [DEFAULT]"		
		}
		["$k": "$v"]
	}
}


private getOverloadOptions() {
	def options = [:]
	(1..10).each {
		options["${it}"] = "${it} A"
	}	
	return options
}

private getPowerReportingThresholdOptions() {
	def options = [0:"Disabled"]
	[1,2,3,4,5,10,25,50,75,100,150,200,250,300,400,500,750,1000,1250,1500,1750,2000,2500,3000,3500,4000,4500,5000].each {
		options["${it}"] = "${it} W"
	}
	return options
}

private getFrequencyOptions() {
	def options = [:]
	options = getTimeOptionsRange(options, "Second", 1, [5,10,15,30,45])
	options = getTimeOptionsRange(options, "Minute", 60, [1,2,3,4,5,10,15,30,45])
	options = getTimeOptionsRange(options, "Hour", (60 * 60), [1,2,3,6,9,12,24])
	return options
}

private getAutoOnOffIntervalOptions() {
	def options = [:]
	options = getTimeOptionsRange(options, "Minute", 1, [1,2,3,4,5,6,7,8,9,10,15,20,25,30,45])
	options = getTimeOptionsRange(options, "Hour", 60, [1,2,3,4,5,6,7,8,9,10,12,18])
	options = getTimeOptionsRange(options, "Day", (60 * 24), [1,2,3,4,5,6])
	options = getTimeOptionsRange(options, "Week", (60 * 24 * 7), [1,2])
	return options
}

private getMainSwitchDelayOptions() {
	def options = [0:"Disabled",500:"500 Milliseconds"]
	options = getTimeOptionsRange(options, "Second", 1000, [1,2,3,4,5,10])
	return setDefaultOption(options, 0)
}

private getTimeOptionsRange(options, name, multiplier, range) {	
	range?.each {
		options["${(it * multiplier)}"] = "${it} ${name}${it == 1 ? '' : 's'}"
	}
	return options
}

private getEnabledOptions() {
	return [0:"Disabled", 1:"Enabled"]
}


// Settings
private getMainSwitchDelaySetting() {
	return safeToInt(settings?.mainSwitchDelay)
}

private executeSendEvent(child, evt) {
	if (evt.displayed == null) {
		evt.displayed = (getAttrVal(evt.name, child) != evt.value)
	}

	if (evt) {
		if (child) {
			if (evt.descriptionText) {
				evt.descriptionText = evt.descriptionText.replace(device.displayName, child.displayName)
				logDebug "${evt.descriptionText}"
			}
			child.sendEvent(evt)						
		}
		else {
			sendEvent(evt)
		}
	}
}

private createEventMap(name, value, displayed=null, unit=null) {	
	def eventMap = [
		name: name,
		value: value,
		displayed: displayed,
		isStateChange: true,
		descriptionText: "${device.displayName} - ${name} is ${value}"
	]
	
	if (unit) {
		eventMap.unit = unit
		eventMap.descriptionText = "${eventMap.descriptionText} ${unit}"
	}	
	return eventMap
}

private getAttrVal(attrName, child=null) {
	try {
		if (child) {
			return child?.currentValue("${attrName}")
		}
		else {
			return device?.currentValue("${attrName}")
		}
	}
	catch (ex) {
		logTrace "$ex"
		return null
	}
}

private findChildByEndPoint(endPoint) {
	def dni = getChildDeviceNetworkId(endPoint)
	return findChildByDeviceNetworkId(dni)
}

private findChildByDeviceNetworkId(dni) {
	return childDevices?.find { it.deviceNetworkId == dni }
}

private getEndPoint(childDeviceNetworkId) {
	return safeToInt((1..3).find {
		"${childDeviceNetworkId}".endsWith("-${getEndPointName(it)?.toUpperCase()}")
	})
}

private getChildDeviceNetworkId(endPoint) {
	return "${device.deviceNetworkId}-${getEndPointName(endPoint).toUpperCase()}"
}

private getEndPointName(endPoint) {
	switch (endPoint) {
		case 1:
			return "left"
			break
		case 2:
			return "right"
			break
		case 3:
			return "usb"
			break
		default:
			return ""
	}
}


private safeToInt(val, defaultVal=0) {
	return "${val}"?.isInteger() ? "${val}".toInteger() : defaultVal
}

private safeToDec(val, defaultVal=0) {
	return "${val}"?.isBigDecimal() ? "${val}".toBigDecimal() : defaultVal
}

private roundTwoPlaces(val) {
	return Math.round(safeToDec(val) * 100) / 100
}

private convertToLocalTimeString(dt) {
	def timeZoneId = location?.timeZone?.ID
	if (timeZoneId) {
		return dt.format("MM/dd/yyyy hh:mm:ss a", TimeZone.getTimeZone(timeZoneId))
	}
	else {
		return "$dt"
	}	
}

private isDuplicateCommand(lastExecuted, allowedMil) {
	!lastExecuted ? false : (lastExecuted + allowedMil > new Date().time) 
}

private logDebug(msg) {
	if (settings?.debugOutput != false) {
		log.debug "$msg"
	}
}

private logTrace(msg) {
	// log.trace "$msg"
}
