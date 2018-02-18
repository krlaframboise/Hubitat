/**
 *  HUBITAT: Ecolink Wireless Switch v1.0.2
 *  (Models: TLS-ZWAVE5, DLS-ZWAVE5, DDLS2-ZWAVE5)
 *
 *  Author: 
 *    Kevin LaFramboise (krlaframboise)
 *
 *  Changelog:
 *    1.0.2 (02/18/2018)
 *      - Initial Release
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

private getDriverDetails() { 
	return "<br>Ecolink Wireless Switch<br>Version 1.0.2<br>Supported Devices: TLS-ZWAVE5, DLS-ZWAVE5, DDLS2-ZWAVE5"
} 

metadata {
	definition (
		name: "Ecolink Wireless Switch", 
		namespace: "krlaframboise", 
		author: "Kevin LaFramboise",
		ocfDeviceType: "oic.d.switch"
	) {
		capability "Actuator"
		capability "Sensor"		
 		capability "Switch"
		capability "Light"
		capability "Battery"
		capability "Refresh"
		// capability "Health Check"
				
		attribute "lastCheckIn", "string"
				
		fingerprint deviceId: "0001", inClusters: "0x5E,0x86,0x72,0x73,0x80,0x25,0x85,0x59,0x7A", outClusters: "", mfr:"014A", prod:"0006", deviceJoinName: "Ecolink Rocker Switch"
		
		fingerprint deviceId: "0002", inClusters: "0x5E,0x86,0x72,0x73,0x80,0x25,0x85,0x59,0x7A", outClusters: "", mfr:"014A", prod:"0006", deviceJoinName: "Ecolink Toggle Switch"

		fingerprint deviceId: "0003", inClusters: "0x5E,0x86,0x72,0x73,0x80,0x25,0x85,0x59,0x7A", outClusters: "", mfr:"014A", prod:"0006", deviceJoinName: "Ecolink Double Rocker Switch"
	}

	preferences { 
		input "checkInInterval", "enum",
			title: "Check In Interval:",
			defaultValue: checkInIntervalSetting,
			required: false,
			displayDuringSetup: true,
			options: checkInIntervalOptions.collect { it.name }
		input "debugOutput", "bool", 
			title: "Enable debug logging?", 
			defaultValue: true, 
			required: false
	}
}

def installed() {
	logTrace "installed()"
	
	updateDriverDetails()
	
	return delayBetween([
		switchBinaryGetCmd(), 
		batteryGetCmd()
	], 500)	
}

def updated() {		
	logTrace "updated()"
		
	initializeCheckInSchedule()
	
	updateDriverDetails()
}

private updateDriverDetails() {
	if (driverDetails != getDataValue("driverDetails")) {
		updateDataValue("driverDetails", driverDetails)
	}
}

private initializeCheckInSchedule(){
	// sendEvent(name: "checkInterval", value: ((checkInIntervalSettingMinutes * 2 * 60) + (60 * 2)), displayed: false, data: [protocol: "zwave", hubHardwareId: device.hub.hardwareID, offlinePingable: "1"])

	unschedule()
	
	switch (checkInIntervalSetting) {
		case "15 Minutes":
			runEvery15Minutes(scheduledCheckIn)
			break
		case "30 Minutes":
			runEvery30Minutes(scheduledCheckIn)
			break
		case { it in ["1 Hour", "2 Hours"] }:
			runEvery1Hour(scheduledCheckIn)
			break
		default:
			runEvery3Hours(scheduledCheckIn)
	}
}

def scheduledCheckIn() {
	def result = []
	if (canCheckIn()) {
		logTrace "scheduledCheckIn()"
		result << batteryGetCmd()
	}
	else {
		logTrace "Ignored scheduled check in"
	}
	return result
}

private canCheckIn() {
	def checkInIntervalMS = ((checkInIntervalSettingMinutes * 60 * 1000) - 5000) // Subtract 5 seconds because the last check in time i stored when the device responses which is a couple of seconds after the scheduled method executes.
	return (!state.lastCheckIn || ((new Date().time) - state.lastCheckIn > checkInIntervalMS)) 
}

// def ping() {
	// logTrace "ping()"	
	// return refresh()
// }

def refresh() {
	logTrace "refresh()"
	def result = []
	result << switchBinaryGetCmd()
	result << batteryGetCmd()	
	return delayBetween(result, 500)
}

def on() {	
	logDebug "on()"
	return toggleSwitch(0xFF)
}

def off() {
	logDebug "off()"
	return toggleSwitch(0x00)
}

private toggleSwitch(val) {
	state.pendingValue = val
	return [
		switchBinarySetCmd(val)
	]
}

private switchBinaryGetCmd() {
	return zwave.switchBinaryV1.switchBinaryGet().format()
}

private switchBinarySetCmd(val) {
	return zwave.switchBinaryV1.switchBinarySet(switchValue: val).format()
}

private batteryGetCmd() {
	return zwave.batteryV1.batteryGet().format()
}


def parse(String description) {
	// logTrace "description: $description"
	def result = []
	
	updateLastCheckIn()
		
	if ("$description".contains("command: 5E02,")) {
		logTrace "Ignoring Zwave Plus Command Class because it causes zwave.parse to throw a null exception"
	}
	else {
		def cmd = zwave.parse(description, commandClassVersions)
		if (cmd) {			
			result += zwaveEvent(cmd)
		}	
	}
	return result
}

private updateLastCheckIn() {
	if (!isDuplicateCommand(state.lastCheckInTime, 60000)) {
		state.lastCheckInTime = new Date().time
		sendEvent(name: "lastCheckIn", value: convertToLocalTimeString(new Date()), displayed: false, isStateChange: true)	
	}
}

private getCommandClassVersions() {
	[
		0x20: 1,	// Basic
		0x25: 1,	// Switch Binary
		0x59: 1,	// AssociationGrpInfo
		0x5E: 2,	// ZwaveplusInfo
		0x72: 2,	// ManufacturerSpecific
		0x73: 1,	// Powerlevel
		0x7A: 2,	// Firmware Update Md
		0x80: 1,	// Battery
		0x85: 2,	// Association
		0x86: 1		// Version (2)
	]
}

def zwaveEvent(hubitat.zwave.commands.basicv1.BasicReport cmd) {
	logTrace "BasicReport: $cmd"	
	return []
}

def zwaveEvent(hubitat.zwave.commands.switchbinaryv1.SwitchBinaryReport cmd) {
	logTrace "SwitchBinaryReport: $cmd"
	
	def type = (state.pendingValue == cmd.value) ? "digital" : "physical"
	state.pendingValue = null
		
	return [
		createEvent(name: "switch", value: cmd.value ? "on" : "off", type: type)
	]
}

def zwaveEvent(hubitat.zwave.commands.batteryv1.BatteryReport cmd) {
	logTrace "BatteryReport: $cmd"
	state.lastCheckIn = new Date().time
	def val = (cmd.batteryLevel == 0xFF ? 1 : cmd.batteryLevel)
	if (val >= 99) {
		val = 100
	}
	return [
		createEvent(name: "battery", value: val, unit: "%")
	]
}

def zwaveEvent(hubitat.zwave.Command cmd) {
	logDebug "Unexpected Command: $cmd"
	return []
}


private getCheckInIntervalSettingMinutes() {
	return convertOptionSettingToInt(checkInIntervalOptions, checkInIntervalSetting) ?: 720
}

private getCheckInIntervalSetting() {
	return settings?.checkInInterval ?: findDefaultOptionName(checkInIntervalOptions)
}


private getCheckInIntervalOptions() {
	[
		[name: "15 Minutes", value: 15],
		[name: "30 Minutes", value: 30],
		[name: "1 Hour", value: 60],
		[name: "2 Hours", value: 120],
		[name: "3 Hours", value: 180],
		[name: "6 Hours", value: 360],
		[name: "9 Hours", value: 540],
		[name: formatDefaultOptionName("12 Hours"), value: 720],
		[name: "18 Hours", value: 1080],
		[name: "24 Hours", value: 1440]
	]
}

private convertOptionSettingToInt(options, settingVal) {
	return safeToInt(options?.find { "${settingVal}" == it.name }?.value, 0)
}

private formatDefaultOptionName(val) {
	return "${val}${defaultOptionSuffix}"
}

private findDefaultOptionName(options) {
	def option = options?.find { it.name?.contains("${defaultOptionSuffix}") }
	return option?.name ?: ""
}

private getDefaultOptionSuffix() {
	return "   (Default)"
}

private safeToInt(val, defaultVal=-1) {
	return "${val}"?.isInteger() ? "${val}".toInteger() : defaultVal
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
	return !lastExecuted ? false : (lastExecuted + allowedMil > new Date().time) 
}

private logDebug(msg) {
	if (settings?.debugOutput || settings?.debugOutput == null) {
		log.debug "$msg"
	}
}

private logTrace(msg) {
	// log.trace "$msg"
}
