/*
 *  Zooz Remote Switch ZEN34 Advanced 	v1.0.1
 *
 *  Changelog:
 *
 *    1.0.1 (01/10/2021)
 *      - Fixes for latest firmware
 *
 *    1.0 (11/14/2020)
 *      - Initial Release
 *
 *
 *  Copyright 2021 Zooz
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
*/


import groovy.transform.Field

@Field static Map commandClassVersions = [
	0x20: 1,	// Basic
	0x26: 3,	// Switch Multilevel (4)
	0x55: 1,	// Transport Service
	0x59: 1,	// AssociationGrpInfo
	0x5A: 1,	// DeviceResetLocally
	0x5B: 1,	// CentralScene (3)
	0x5E: 2,	// ZwaveplusInfo
	0x6C: 1,	// Supervision
	0x70: 2,	// Configuration
	0x72: 2,	// ManufacturerSpecific
	0x73: 1,	// Powerlevel
	0x7A: 2,	// Firmware Update Md (3)
	0x80: 1,	// Battery
	0x84: 2,	// WakeUp
	0x85: 2,	// Association
	0x86: 1,	// Version (2)
	0x8E: 2,	// MultiChannelAssociation (3)
	0x9F: 1		// Security 2
]

@Field static int wakeUpInterval = 43200

@Field static Map ledModeOptions = [0:"LED always off", 1:"LED on when button is pressed [DEFAULT]", 2:"LED always on in specified upper paddle color", 3:"LED always on in specified lower paddle color"]

@Field static Map upperLedColorOptions = [0:"White", 1:"Blue [DEFAULT]", 2:"Green", 3:"Red", 4:"Magenta", 5:"Yellow", 6:"Cyan"]

@Field static Map lowerLedColorOptions = [0:"White [DEFAULT]", 1:"Blue", 2:"Green", 3:"Red", 4:"Magenta", 5:"Yellow", 6:"Cyan"]

@Field static Map associationGroups = [2:"associationGroupTwo", 3:"associationGroupThree"]


metadata {
	definition (
		name:"Zooz Remote Switch ZEN34 Advanced", 
		namespace:"Zooz", 
		author: "Kevin LaFramboise (krlaframboise)", 
		importUrl: "https://raw.githubusercontent.com/krlaframboise/Hubitat/master/drivers/zooz/zooz-remote-switch-zen34-advanced.src/zooz-remote-switch-zen34-advanced.groovy"
	) {
		capability "Sensor"
		capability "Battery"
		capability "PushableButton"
		capability "DoubleTapableButton"
		capability "HoldableButton"
		capability "ReleasableButton"
		capability "Refresh"

		attribute "pushed3x", "number"
		attribute "pushed4x", "number"
		attribute "pushed5x", "number"
		attribute "associationGroupTwo", "string"
		attribute "associationGroupThree", "string"
		attribute "syncStatus", "string"

		fingerprint deviceId: "F001", mfr: "0312", prod: "0004", inClusters:"0x5E,0x55,0x9F,0x6C", deviceJoinName: "Zooz Remote Switch ZEN34 Advanced"
		fingerprint deviceId: "F001", mfr: "0312", prod: "0004", inClusters:"0x5E,0x85,0x8E,0x59,0x55,0x86,0x72,0x5A,0x73,0x80,0x5B,0x26,0x9F,0x70,0x84,0x6C,0x7A", deviceJoinName: "Zooz Remote Switch ZEN34 Advanced"
		fingerprint deviceId: "F001", mfr: "027A", prod: "0004", inClusters:"0x5E,0x55,0x9F,0x6C", deviceJoinName: "Zooz Remote Switch ZEN34 Advanced"
		fingerprint deviceId: "F001", mfr: "027A", prod: "0004", inClusters:"0x5E,0x85,0x8E,0x59,0x55,0x86,0x72,0x5A,0x73,0x80,0x5B,0x26,0x9F,0x70,0x84,0x6C,0x7A", deviceJoinName: "Zooz Remote Switch ZEN34 Advanced"
		fingerprint deviceId: "F001", mfr: "027A", prod: "7000", inClusters:"0x5E,0x55,0x9F,0x6C", deviceJoinName: "Zooz Remote Switch ZEN34 Advanced"
		fingerprint deviceId: "F001", mfr: "027A", prod: "7000", inClusters:"0x5E,0x85,0x8E,0x59,0x55,0x86,0x72,0x5A,0x73,0x80,0x5B,0x26,0x9F,0x70,0x84,0x6C,0x7A", deviceJoinName: "Zooz Remote Switch ZEN34 Advanced"
	}

	preferences {
		configParams.each {
			createEnumInput("configParam${it.num}", "${it.name}:", it.value, it.options)
		}

		input "assocInstructions", "paragraph",
			title: "<strong>Device Associations</strong>",
			description: "<p>Associations are an advance feature that allow you to establish direct communication between Z-Wave devices.  To make the Zooz Remote Switch control another Z-Wave device, get that device's DNI from the devices page and enter it into one of the assoction settings.  Group 2 and Group 3 both support up to 10 DNIs and you can use commas to separate them.</p>",
			required: false

		input "assocWarning", "paragraph",
			title: "<strong>Associations Warning</strong>",
			description: "<p>If you add a device's DNI to the Group 2 or Group 3 setting and then later remove that device from Hubitat, you <strong>MUST</strong> come back and remove it from the setting.  Failing to do that will substantially increase the number of z-wave messages being sent by this device and could affect the stability of your z-wave mesh.</p>",
			required: false

		input "group2AssocDNIs", "string",
			title: "Enter DNIs for Association Group 2 (On/Off):",
			required: false

		input "group3AssocDNIs", "string",
			title: "Enter DNIs for Association Group 3 (Dimming):",
			required: false

		input "debugOutput", "bool",
			title: "Enable debug logging?",
			defaultValue: true,
			required: false
	}
}

void createEnumInput(String name, String title, Integer defaultVal, Map options) {
	input name, "enum",
		title: title,
		required: false,
		defaultValue: defaultVal.toString(),
		options: options
}


void installed() {
	logDebug "installed()..."

	state.refreshAll = true

	initialize()
}


void updated() {
	logDebug "updated()..."

	initialize()

	if (pendingChanges) {
		logForceWakeupMessage "The setting changes will be sent to the device the next time it wakes up."
	}

	refreshSyncStatus()
}

void initialize() {
	if (!device.currentValue("numberOfButtons")) {
		sendEvent(name:"numberOfButtons", value:2)
	}

	if (!device.currentValue("pushed")) {
		sendEvent(name: "pushed", value: 1)
		sendEvent(name: "held", value: 1)
		sendEvent(name: "released", value: 1)
		sendEvent(name: "doubleTapped", value: 1)
		sendEvent(name: "pushed3x", value: 1)
		sendEvent(name: "pushed4x", value: 1)
		sendEvent(name: "pushed5x", value: 1)
	}

	if (device.currentValue("associationGroupTwo") == null) {
		sendEvent(name: "associationGroupTwo", value: "")
	}

	if (device.currentValue("associationGroupThree") == null) {
		sendEvent(name: "associationGroupThree", value: "")
	}
}


void configure() {
	logDebug "configure()..."

	sendCommands(getConfigureCmds())
}

List<String> getConfigureCmds() {
	List<String> cmds = []

	int changes = pendingChanges
	if (changes) {
		log.warn "Syncing ${changes} Change(s)"
	}

	if (state.refreshAll || !getDataValue("firmwareVersion")) {
		logDebug "Requesting Version Report"
		cmds << versionGetCmd()
	}

	if (state.refreshAll || !device.currentValue("battery")) {
		logDebug "Requesting Battery Report"
		cmds << batteryGetCmd()
	}

	if (state.wakeUpInterval != wakeUpInterval) {
		logDebug "Setting Wake Up Interval to ${wakeUpInterval} Seconds"
		cmds << wakeUpIntervalSetCmd(wakeUpInterval)
		cmds << wakeUpIntervalGetCmd()
	}

	configParams.each { param ->
		Integer storedVal = getParamStoredValue(param.num)
		if (state.refreshAll || storedVal != param.value) {
			logDebug "Changing ${param.name}(#${param.num}) from ${storedVal} to ${param.value}"
			cmds << configSetCmd(param, param.value)
			cmds << configGetCmd(param)
		}
	}

	cmds += getConfigureAssocsCmds()

	state.refreshAll = false

	return cmds
}


List<String> getConfigureAssocsCmds() {
	List<String> cmds = []

	associationGroups.each { group, name ->
		boolean changes = false

		def stateNodeIds = state["${name}NodeIds"]
		def settingNodeIds = getAssocDNIsSettingNodeIds(group)

		def newNodeIds = settingNodeIds?.findAll { !(it in stateNodeIds) }
		if (newNodeIds) {
			logDebug "Adding Nodes ${newNodeIds} to Association Group ${group}"
			cmds << associationSetCmd(group, newNodeIds)
			changes = true
		}

		def oldNodeIds = stateNodeIds?.findAll { !(it in settingNodeIds) }
		if (oldNodeIds) {
			logDebug "Removing Nodes ${oldNodeIds} from Association Group ${group}"
			cmds << associationRemoveCmd(group, oldNodeIds)
			changes = true
		}

		if (changes || state.refreshAll) {
			cmds << associationGetCmd(group)
		}
	}
	return cmds
}

List<Integer> getAssocDNIsSettingNodeIds(int group) {
	String assocSetting = settings["group${group}AssocDNIs"] ?: ""

	List<Integer> nodeIds = convertHexListToIntList(assocSetting?.split(","))

	if (assocSetting && !nodeIds) {
		log.warn "'${assocSetting}' is not a valid value for the 'Device Network Ids for Association Group ${group}' setting.  All z-wave devices have a 2 character Device Network Id and if you're entering more than 1, use commas to separate them."
	}
	else if (nodeIds?.size() >  10) {
		log.warn "The 'Device Network Ids for Association Group ${group}' setting contains more than 10 Ids so only the first 10 will be associated."
	}

	return nodeIds
}


void refresh() {
	logDebug "refresh()..."

	refreshSyncStatus()
	state.refreshAll = true

	logForceWakeupMessage "The next time the device wakes up, the sensor data will be requested."
}


void sendCommands(List<String> cmds, Integer delay=300) {
	if (cmds) {
		if (delay != null) {
			cmds = delayBetween(cmds, delay)
		}
		sendHubCommand(new hubitat.device.HubMultiAction(cmds, hubitat.device.Protocol.ZWAVE))
	}
}


String associationSetCmd(int group, nodes) {
	return secureCmd(zwave.associationV2.associationSet(groupingIdentifier: group, nodeId: nodes))
}

String associationRemoveCmd(int group, nodes) {
	return secureCmd(zwave.associationV2.associationRemove(groupingIdentifier: group, nodeId: nodes))
}

String associationGetCmd(int group) {
	return secureCmd(zwave.associationV2.associationGet(groupingIdentifier: group))
}

String wakeUpIntervalSetCmd(seconds) {
	return secureCmd(zwave.wakeUpV2.wakeUpIntervalSet(seconds:seconds, nodeid:zwaveHubNodeId))
}

String wakeUpIntervalGetCmd() {
	return secureCmd(zwave.wakeUpV2.wakeUpIntervalGet())
}

String wakeUpNoMoreInfoCmd() {
	return secureCmd(zwave.wakeUpV2.wakeUpNoMoreInformation())
}

String versionGetCmd() {
	return secureCmd(zwave.versionV1.versionGet())
}

String batteryGetCmd() {
	return secureCmd(zwave.batteryV1.batteryGet())
}

String configSetCmd(Map param, Integer value) {
	return secureCmd(zwave.configurationV1.configurationSet(parameterNumber: param.num, size: param.size, scaledConfigurationValue: value))
}

String configGetCmd(Map param) {
	return secureCmd(zwave.configurationV1.configurationGet(parameterNumber: param.num))
}

String supervisionReportCmd(hubitat.zwave.Command cmd) {
	return secureCmd(zwave.supervisionV1.supervisionReport(sessionID: cmd.sessionID, reserved: 0, moreStatusUpdates: false, status: 0xFF, duration: 0))
}

String secureCmd(cmd) {
	try {
		return zwaveSecureEncap(cmd.format())
	}
	catch (ex) {
		// support for older hub firmware.
		return cmd.format()
	}
}


void parse(String description) {
	def cmd = zwave.parse(description, commandClassVersions)
	if (cmd) {
		zwaveEvent(cmd)
	}
}


void zwaveEvent(hubitat.zwave.commands.securityv1.SecurityMessageEncapsulation cmd) {
	hubitat.zwave.Command encapCmd = cmd.encapsulatedCommand(commandClassVersions)
	if (encapCmd) {
		zwaveEvent(encapCmd)
	}
	else {
		log.warn "Unable to extract encapsulated cmd from $cmd"
	}
}

void zwaveEvent(hubitat.zwave.commands.supervisionv1.SupervisionGet cmd) {
	logTrace "$cmd"

    hubitat.zwave.Command encapCmd = cmd.encapsulatedCommand(commandClassVersions)
    if (encapCmd) {
        zwaveEvent(encapCmd)
    }
	else {
		log.warn "Unable to extract encapsulated cmd from $cmd"
	}

	sendCommands([supervisionReportCmd(cmd)], null)    
}


void zwaveEvent(hubitat.zwave.commands.wakeupv2.WakeUpIntervalReport cmd) {
	logTrace "$cmd"

	runIn(3, refreshSyncStatus)

	state.wakeUpInterval = cmd.seconds
}


void zwaveEvent(hubitat.zwave.commands.wakeupv2.WakeUpNotification cmd) {
	logDebug "Device Woke Up..."
	
	runIn(3, refreshSyncStatus)
	state.hasWokenUp = true

	List<String> cmds = getConfigureCmds()
	if (cmds) {
		cmds << "delay 1000"
	}
	cmds << wakeUpNoMoreInfoCmd()

	sendCommands(cmds)
}


void zwaveEvent(hubitat.zwave.commands.configurationv2.ConfigurationReport cmd) {
	logTrace "${cmd}"

	runIn(3, refreshSyncStatus)

	Map param = configParams.find { it.num == cmd.parameterNumber }
	if (param) {
		int val = cmd.scaledConfigurationValue

		setParamStoredValue(param.num, val)

		logDebug "${param.name}(#${param.num}) = ${val}"
	}
	else {
		logDebug "Parameter #${cmd.parameterNumber} = ${cmd.scaledConfigurationValue}"
	}
}


void zwaveEvent(hubitat.zwave.commands.associationv2.AssociationReport cmd) {
	logTrace "$cmd"

	runIn(3, refreshSyncStatus)

	logDebug "Group ${cmd.groupingIdentifier} Association: ${cmd.nodeId}"

	String name = associationGroups.get(safeToInt(cmd.groupingIdentifier))
	if (name) {
		state["${name}NodeIds"] = cmd.nodeId

		def dnis = convertIntListToHexList(cmd.nodeId)?.join(", ") ?: ""
		sendEventIfNew(name, (dnis ?: "none"))
	}
}


void zwaveEvent(hubitat.zwave.commands.versionv1.VersionReport cmd) {
	String subVersion = String.format("%02d", cmd.applicationSubVersion)
	String fullVersion = "${cmd.applicationVersion}.${subVersion}"

	logDebug "Firmware Version: ${fullVersion}"

	if (getDataValue("firmwareVersion") != fullVersion) {
		updateDataValue("firmwareVersion", fullVersion)
	}
}


void zwaveEvent(hubitat.zwave.commands.batteryv1.BatteryReport cmd) {
	int val = (cmd.batteryLevel == 0xFF ? 1 : cmd.batteryLevel)

	if (val > 100) {
		val = 100
	}

	logDebug "Battery is ${val}%"
	sendEvent(name:"battery", value:val, unit:"%", isStateChange: true)
}


void zwaveEvent(hubitat.zwave.commands.centralscenev1.CentralSceneNotification cmd){
	if (state.lastSequenceNumber != cmd.sequenceNumber) {
		state.lastSequenceNumber = cmd.sequenceNumber

		String btn = cmd.sceneNumber
		String action
		switch (cmd.keyAttributes){
			case 0:
				action = "pushed"
				break
			case 1:
				action = "released"
				break
			case 2:
				action = "held"
				break
			case 3:
				action = "doubleTapped"
				break
			case { it >= 4 && it <= 6}:
				action = "pushed${cmd.keyAttributes - 1}x"
				break
			default:
				logTrace "keyAttributes ${cmd.keyAttributes} not supported"
		}

		if (action) {
			logDebug "Button ${btn} ${action}"
			sendEvent(name: action, value: btn, isStateChange: true)
		}
		
		if (!state.hasWokenUp) {
			// device hasn't been put to sleep after inclusion and is draining the battery so put it to sleep.
			state.hasWokenUp = true
			sendCommands([wakeUpNoMoreInfoCmd()])
		}
	}
}


void zwaveEvent(hubitat.zwave.Command cmd) {
	logDebug "Unhandled zwaveEvent: $cmd"
}


List<Map> getConfigParams() {
	return [
		ledModeParam,
		upperPaddleLedColorParam,
		lowerPaddleLedColorParam
	]
}

Map getLedModeParam() {
	return getParam(1, "LED Indicator Mode", 1, 1, ledModeOptions)
}

Map getUpperPaddleLedColorParam() {
	return getParam(2, "Upper Paddled LED Indicator Color", 1, 1,  upperLedColorOptions)
}

Map getLowerPaddleLedColorParam() {
	return getParam(3, "Lower Paddle LED Indicator Color", 1, 0, lowerLedColorOptions)
}

Map getParam(Integer num, String name, Integer size, Integer defaultVal, Map options) {
	Integer val = safeToInt((settings ? settings["configParam${num}"] : null), defaultVal)

	return [num: num, name: name, size: size, value: val, options: options]
}


Integer getParamStoredValue(Integer paramNum, Integer defaultVal=null) {
	return safeToInt(state["configVal${paramNum}"] , defaultVal)
}

void setParamStoredValue(Integer paramNum, Integer value) {
	state["configVal${paramNum}"] = value
}


void refreshSyncStatus() {
	int changes = pendingChanges
	sendEventIfNew("syncStatus", (changes ?  "${changes} Pending Change(s)<br>Tap Up x 7" : "Synced"))
}


int getPendingChanges() {
	int configChanges = safeToInt(configParams.count { it.value != getParamStoredValue(it.num) })
	int pendingWakeUpInterval = (state.wakeUpInterval != wakeUpInterval ? 1 : 0)
	int pendingAssocs = (getConfigureAssocsCmds()?.size() ? 1 : 0)

	return (configChanges + pendingWakeUpInterval + pendingAssocs)
}


void logForceWakeupMessage(String msg) {
	log.warn "${msg}  You can force the device to wake up immediately by tapping the upper paddle 7x."
}


void sendEventIfNew(String name, value) {
	String desc = "${device.displayName}: ${name} is ${value}"
	if (device.currentValue(name) != value) {
		logDebug(desc)
		sendEvent(name: name, value: value, descriptionText: desc)
	}
}


List<String> convertIntListToHexList(List<Integer> intList) {
	List<String> hexList = []
	intList?.each {
		hexList.add(Integer.toHexString(it).padLeft(2, "0").toUpperCase())
	}
	return hexList
}

List<Integer> convertHexListToIntList(String[] hexList) {
	List<Integer> intList = []

	hexList?.each {
		try {
			it = it.trim()
			intList.add(Integer.parseInt(it, 16))
		}
		catch (e) { }
	}
	return intList
}


Integer safeToInt(val, Integer defaultVal=0) {
	if ("${val}"?.isInteger()) {
		return "${val}".toInteger()
	}
	else if ("${val}".isDouble()) {
		return "${val}".toDouble()?.round()
	}
	else {
		return  defaultVal
	}
}


void logDebug(String msg) {
	if (settings?.debugOutput || settings?.debugOutput == null) {
		log.debug(msg)
	}
}


void logTrace(String msg) {
	 // log.trace(msg)
}
