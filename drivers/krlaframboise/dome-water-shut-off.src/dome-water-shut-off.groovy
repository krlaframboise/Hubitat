/**
 *  HUBITAT: Dome Water Shut-Off v0.0.0
 *  (Model: DMWV1)
 *
 *  Author: 
 *    Kevin LaFramboise (krlaframboise)
 *
 *  Changelog:
 *
 *    0.0.0 (02/20/2018)
 *      - Beta Release
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
	return "<br>Dome Water Shut-Off<br>Version 0.0.0<br>Supported Devices: DMWV1"
}
 
 metadata {
	definition (
		name: "Dome Water Shut-Off", 
		namespace: "krlaframboise", 
		author: "Kevin LaFramboise"
	) {
		capability "Actuator"
		capability "Sensor"
		capability "Valve"
		capability "Switch"
		capability "Refresh"
		// capability "Health Check"
		
		attribute "status", "enum", ["open", "closed", "opening", "closing"]
		attribute "lastCheckin", "string"
		
		fingerprint deviceId: "0002", inClusters: "0x5E,0x86,0x72,0x5A,0x73,0x85,0x59,0x25,0x20,0x27", outClusters: "", mfr:"021F", prod:"0003", deviceJoinName: "Dome Water Shut-Off"
	}
		
	preferences {
		input "checkinInterval", "enum",
			title: "Checkin Interval:",
			defaultValue: checkinIntervalSetting,
			required: false,
			displayDuringSetup: true,
			options: checkinIntervalOptions.collect { it.name }
		input "debugOutput", "bool", 
			title: "Enable debug logging?", 
			defaultValue: true, 
			required: false
	}
}

def installed() {
	logTrace "installed()"
	updateDriverDetails()
	return []
}

def updated() {			
	logTrace "updated()"		
	
	// initializeCheckin()
	startHealthPollSchedule()
	
	updateDriverDetails()
	
	return refresh()
}

private updateDriverDetails() {
	if (driverDetails != getDataValue("driverDetails")) {
		updateDataValue("driverDetails", driverDetails)
	}
}

// private initializeCheckin() {
	// // Set the Health Check interval so that it can be skipped once plus 2 minutes.
	// def checkInterval = ((checkinIntervalSettingMinutes * 2 * 60) + (2 * 60))
	
	// sendEvent(name: "checkInterval", value: checkInterval, displayed: false, data: [protocol: "zwave", hubHardwareId: device.hub.hardwareID])
	
	// startHealthPollSchedule()
// }

private startHealthPollSchedule() {
	unschedule(healthPoll)
	switch (checkinIntervalSettingMinutes) {
		case 5:
			runEvery5Minutes(healthPoll)
			break
		case 10:
			runEvery10Minutes(healthPoll)
			break
		case 15:
			runEvery15Minutes(healthPoll)
			break
		case 30:
			runEvery30Minutes(healthPoll)
			break
		case [60, 120]:
			runEvery1Hour(healthPoll)
			break
		default:
			runEvery3Hours(healthPoll)			
	}
}

def healthPoll() {
	logTrace "healthPoll()"
	return [versionGetCmd()]
}

// def ping() {
	// logTrace "ping()"
	// // Don't allow it to ping the device more than once per minute.
	// if (!isDuplicateCommand(state.lastCheckinTime, 60000)) {
		// logDebug "Attempting to ping device."
		// // Restart the polling schedule in case that's the reason why it's gone too long without checking in.
		// startHealthPollSchedule()
		
		// return versionGetCmd()
	// }	
// }

// Refreshes the valve status.
def refresh() {
	logDebug "Requesting valve position."
	return [switchBinaryGetCmd()]
}

// Opens the valve.
def on() {
	return open()
}

// Opens the valve.
def open() {
	logTrace "Executing open()"
	return toggleValve(openStatus)
}

// Closes the valve.
def off() {
	return close()
}

// Closes the valve.
def close() {
	logTrace "Executing close()"
	return toggleValve(closedStatus)
}

private toggleValve(pending) {
	state.pending = pending
	state.pending.abortTime = (new Date().time + (30 * 60 * 1000))
	logDebug "${pending.pendingValue.capitalize()} Valve"
	return [		
		switchBinarySetCmd(pending.cmdValue),
		switchBinaryGetCmd(),
		"delay 9000",
		switchBinaryGetCmd()
	]
}

// Handles device response.
def parse(String description) {
	def result = []

	def cmd = zwave.parse(description, getCommandClassVersions())
	if (cmd) {
		result += zwaveEvent(cmd)
	}
	else {
		logDebug "Unable to parse description: $description"
	}
	
	if (!isDuplicateCommand(state.lastCheckinTime, 60000)) {
		result << createLastCheckinEvent()
	}	
	return result
}

// Requested by health poll to verify that it's still online.
def zwaveEvent(hubitat.zwave.commands.versionv1.VersionReport cmd) {
	logTrace "VersionReport: $cmd"	
	return []
}

// Creates events based on state of valve.
def zwaveEvent(hubitat.zwave.commands.switchbinaryv1.SwitchBinaryReport cmd) {
	logTrace "SwitchBinaryReport: $cmd"
	def result = []
	def reported = (cmd.value == openStatus.cmdValue) ? openStatus : closedStatus
	
	if (state.pending?.abortTime && state.pending?.abortTime > new Date().time) {
		result << createEvent(createEventMap("status", state.pending.pendingValue, false))
		state.pending = [:]
	}
	else {
		logDebug "Valve is ${reported.value.capitalize()}"
		if (device.currentValue("status") != reported.value) {
			result << createEvent(createEventMap("status", reported.value, false))
		}
		result << createEvent(createEventMap("switch", reported.switchValue, false))
		result << createEvent(createEventMap("valve", reported.value))	
	}	
	return result
}

// Handles unexpected device event.
def zwaveEvent(hubitat.zwave.Command cmd) {
	logDebug "Unhandled Command: $cmd"
	return []
}


private versionGetCmd() {
	return zwave.versionV1.versionGet().format()
}

private switchBinaryGetCmd() {
	return zwave.switchBinaryV1.switchBinaryGet().format()
}

private switchBinarySetCmd(val) {
	return zwave.switchBinaryV1.switchBinarySet(switchValue: val).format()
}


private createLastCheckinEvent() {
	logDebug "Device Checked In"
	state.lastCheckinTime = new Date().time
	return createEvent(name: "lastCheckin", value: convertToLocalTimeString(new Date()), displayed: false)
}

private convertToLocalTimeString(dt) {
	return dt.format("MM/dd/yyyy hh:mm:ss a", TimeZone.getTimeZone(location.timeZone.ID))
}

private createEventMap(name, value, displayed=null) {	
	def isStateChange = (device.currentValue(name) != value)
	displayed = (displayed == null ? isStateChange : displayed)
	def eventMap = [
		name: name,
		value: value,
		displayed: displayed,
		isStateChange: isStateChange
	]
	logTrace "Creating Event: ${eventMap}"
	return eventMap
}

private getCommandClassVersions() {
	[
		0x59: 1,  // AssociationGrpInfo
		0x5A: 1,  // DeviceResetLocally
		0x5E: 2,  // ZwaveplusInfo
		0x72: 2,  // ManufacturerSpecific
		0x73: 1,  // Powerlevel
		0x85: 2,  // Association
		0x86: 1,  // Version (2)
		0x25: 1,  // Switch Binary
		0x27: 1		// Switch All
	]
}

private getOpenStatus() {
	return [
		value: "open",		
		pendingValue: "opening",
		switchValue: "on",
		cmdValue: 0xFF,		
		pendingCmdValue: 0x00
	]
}

private getClosedStatus() { 
	return [
		value: "closed",
		pendingValue: "closing",
		switchValue: "off",
		cmdValue: 0x00,
		pendingCmdValue: 0xFF
	]
}

private getCheckinIntervalSettingMinutes() {
	return convertOptionSettingToInt(checkinIntervalOptions, checkinIntervalSetting) ?: 720
}
private getCheckinIntervalSetting() {
	return settings?.checkinInterval ?: findDefaultOptionName(checkinIntervalOptions)
}

private getCheckinIntervalOptions() {
	[
		[name: "5 Minutes", value: 5],
		[name: "10 Minutes", value: 10],
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

private int safeToInt(val, int defaultVal=0) {
	return "${val}"?.isInteger() ? "${val}".toInteger() : defaultVal
}

private isDuplicateCommand(lastExecuted, allowedMil) {
	!lastExecuted ? false : (lastExecuted + allowedMil > new Date().time) 
}

private logDebug(msg) {
	if (settings?.debugOutput || settings?.debugOutput == null) {
		log.debug "$msg"
	}
}

private logTrace(msg) {
	// log.trace "$msg"
}
