/**
 *  Aeon Labs Doorbell v1.0.2
 *      (Model:ZW056-A)
 *
 *  Changelog:
 *
 *  1.0.2 (02/18/2018)
 *    	-  Initial Release
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

private getDriverDetails() { 
	return "<br>Aeon Labs Doorbell<br>Version 1.0.2<br>Supported Devices: ZW056-A"
}
 
metadata {
	definition (name: "Aeon Labs Doorbell", namespace: "krlaframboise", author: "Kevin LaFramboise") {
		capability "Configuration"
		capability "Actuator"		
		capability "Switch"
		capability "Alarm"
		capability "Tone"
		capability "Speech Synthesis"
		capability "Battery"

		attribute "status", "string"		
		attribute "lastCheckin", "string"
											
		fingerprint deviceId: "0038", inClusters: "0x5E,0x86,0x25,0x70,0x72,0x59,0x85,0x73,0x7A,0x5A", outClusters: "0x82", mfr:"0086", prod:"0104", deviceJoinName: "Aeon Labs Doorbell"
		
		fingerprint deviceId: "0038", inClusters: "0x5E,0x86,0x98,0x25,0x70,0x72,0x59,0x85,0x73,0x7A,0x5A", outClusters: "0x82", mfr:"0086", prod:"0104", deviceJoinName: "Aeon Labs Doorbell"
	}

	preferences {
		input "volume", "number",
			title: "Volume: (0-10, 0=Mute)",
			range: "0..10",
			defaultValue: 10
		input "alarmTrack", "number",
			title: "Alarm Track: (1-100)",
			range: "1..100",
			defaultValue: 4
		input "beepTrack", "number",
			title: "Beep Track: (1-100)",
			range: "1..100",
			defaultValue: 3
		input "doorbellTrack", "number",
			title: "Doorbell Track: (1-100)",
			defaultValue: 2,
			range: "1..100"
		input "repeat", "number",
			title: "Repeat: (1-20)",
			defaultValue: 1,
			range: "1..20"
		input "checkinInterval", "enum",
			title: "Checkin Interval:",
			defaultValue: checkinIntervalSetting,
			options: checkinIntervalOptions.collect { it.name }
		input "logEnable", "bool", 
			title: "Enable debug logging",
			defaultValue: true			
    input "txtEnable", "bool", 
			title: "Enable descriptionText logging",
			defaultValue: true
	}
}

def installed() {
	log.info "updated..."
	updateDriverDetails()
	return []
}

def updated() {
	log.info "updated..."
	log.warn "debug logging is: ${logEnable == true}"
	log.warn "description logging is: ${txtEnable == true}"
	
	updateDriverDetails()
		
	runIn((60 * 60 * 3), logsOff)
	
	startHealthPollSchedule()
			
	def cmds = []
	if (!state.isConfigured) {
		cmds += configure()            
	}
	else {			
		cmds += updateSettings()		
	}	
	return cmds
}

private updateDriverDetails() {
	if (driverDetails != getDataValue("driverDetails")) {
		updateDataValue("driverDetails", driverDetails)
	}
}

private updateSettings() {
	def result = []	
	if (settingChanged("alarmTrack")) {
		storeAlarmTrack(settings?.alarmTrack)
	}
	
	if (settingChanged("beepTrack")) {
		storeBeepTrack(settings?.beepTrack)
	}
	
	if (settingChanged("doorbellTrack")) {
		result += setDoorbellTrack(settings?.doorbellTrack)
	}
	
	if (settingChanged("repeat")) {
		result += setRepeat(settings?.repeat)
	}	
	
	if (settingChanged("volume")) {
		result += setVolume(settings?.volume)
	}
	return result ? delayBetween(result, 500) : []
}

private settingChanged(name) {
	return settings && settings["$name"] != null && settings["$name"] != state["$name"]
}

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
	logTrace "healthPoll()..."
	return [batteryHealthGetCmd()]
}

def configure() {
	log.warn "configure..."

	updateLogSettings(true)	
	runIn(180, logsOff)
	
	if (device.currentValue("status") == null) {
		createOffEventMaps().each {
			sendEvent(it)
		}
	}
	
	storeBeepTrack(3)
	storeAlarmTrack(4)
	
	def cmds = [
		deviceNotifyTypeSetCmd(true),
		sendLowBatterySetCmd(),
		batteryHealthGetCmd()
	]
	cmds += setVolume(settings?.volume)
	cmds += setRepeat(settings?.repeat)
	cmds += setDoorbellTrack(settings?.doorbellTrack)
	return delayBetween(cmds, 500)
}
	
def setVolume(volume) {
	logTrace "setVolume($volume)..."
	return [volumeSetCmd(volume), volumeGetCmd()]	
}

def setRepeat(repeat) {
	logTrace "setRepeat($repeat)..."
	return [repeatSetCmd(repeat), repeatGetCmd()]
}

def setDoorbellTrack(track) {
	logTrace "setDoorbellTrack($track)..."
	return [doorbellSetCmd(track), doorbellGetCmd()]
}

void storeBeepTrack(track) {	
	logTrace "storeBeepTrack($track)..."	
	state.beepTrack = validateTrack(track)
}

void storeAlarmTrack(track) {
	logTrace "storeAlarmTrack($track)..."
	state.alarmTrack = validateTrack(track)
}

def off() {
	logDebug "off()..."
	return playTrackCmds(0)
}

def speak(msg) {
	logDebug "speak($msg)..."
	return playTrackCmds(msg)
}

def on() {
	logDebug "on()..."
	return [playTrackSetCmd(settings?.doorbellTrack), "delay 500"]
}

def strobe() {
	return both()
}

def siren() {
	return both()
}

def both() {
	logDebug "both()..."
	sendEvent(getEventMap("alarm", "both"))
	return playTrackCmds(settings?.alarmTrack)
}

def beep() {
	logDebug "beep()..."
	return playTrackCmds(settings?.beepTrack)
}


def parse(String description) {
	// logTrace "description: ${description}"
	def result = []
	def cmd = zwave.parse(description, commandClassVersions)
	if (cmd) {
		result += zwaveEvent(cmd)
	}
	else {
		log.warn "Unable to parse: $description ($cmd)"
	}
	if (!isDuplicateCommand(state.lastCheckinTime, 60000)) {
		state.lastCheckinTime = new Date().time
		result << createLastCheckinEvent()
	}
	return result
}

private createLastCheckinEvent() {	
	logDebug "Device Checked In"	
	return createEvent(getEventMap("lastCheckin", convertToLocalTimeString(new Date()), false))
}

private convertToLocalTimeString(dt) {
	def timeZoneId = location?.timeZone?.ID
	if (timeZoneId) {
		return dt.format("MM/dd/yyyy hh:mm:ss a", TimeZone?.getTimeZone(timeZoneId))
	}
	else {
		return "$dt"
	}	
}

def zwaveEvent(hubitat.zwave.commands.securityv1.SecurityMessageEncapsulation cmd) {
	def result = []
	if (cmd) {
		def encapCmd = cmd.encapsulatedCommand(commandClassVersions)
		if (encapCmd) {
			result += zwaveEvent(encapCmd)
		}
		else {
			log.warn "Unable to extract encapsulated cmd from $cmd"
		}
	}
	return result
}

private getCommandClassVersions() {
	[
		0x20: 1,	// Basic
		0x25: 1,	// Switch Binary
		0x59: 1,  // AssociationGrpInfo
		0x5A: 1,  // DeviceResetLocally
		0x5E: 2,  // ZwaveplusInfo
		0x70: 2,  // Configuration
		0x72: 2,  // ManufacturerSpecific
		0x73: 1,  // Powerlevel
		0x7A: 2,	// Firmware Update
		0x82: 1,	// Hail
		0x85: 2,  // Association
		0x86: 1,	// Version (2)
		0x98: 1		// Security
	]
}

def zwaveEvent(hubitat.zwave.commands.basicv1.BasicReport cmd) {
	// logTrace "BasicReport[value: ${cmd.value}]"
	def result = []
	if (cmd.value) {
		result << createEvent(getEventMap("switch", "on", false))
		result << createEvent(getStatusEventMap("Doorbell Ringing"))
	}
	else {
		createOffEventMaps().each {
			result << createEvent(it)
		}
	}
	return result
}

private createOffEventMaps() {
	def result = []
	result << getStatusEventMap("off")
	if (device.currentValue("switch") != "off") {
		result << getEventMap("switch", "off", false)
	}
	if (device.currentValue("alarm") != "off") {
		result << getEventMap("alarm", "off")
	}
	return result
}

private getStatusEventMap(status) {
	if (settings?.volume == 0 && status != "off") {
		status = "${status} (MUTED)"
	}
	return getEventMap("status", "$status", true)
}

def zwaveEvent(hubitat.zwave.commands.configurationv2.ConfigurationReport cmd) {
	// logTrace "ConfigurationReport[parameterNumber: ${cmd.parameterNumber}, configurationValue: ${cmd.configurationValue}]"
	state.isConfigured = true
	
	def result = []
	def name = null
	def val = cmd.configurationValue[0]
	
	switch (cmd.parameterNumber) {
		case 8:
			name = "Volume"
			state.volume = val	
			break
		case 5:
			name = "Doorbell Track"
			state.doorbellTrack = val
			break
		case 2:
			name = "Repeat"
			state.repeat = val
			break            
		case 80:
			name = "Device Notification Type"
			state.notificationType = val
			break
		case 81:
			name = "Send Low Battery Notifications"
			break
		case 42:
			result << createEvent(getBatteryEventMap(val))
			break
		default:
			name = "Parameter #${cmd.parameterNumber}"
	}
	if (name) {
		logDebug "${name} = ${val}"
	}
	return result
}

private getBatteryEventMap(val) {
	logTrace "getBatteryEventMap($val)"
	
	def batteryVal = (val == 0) ? 100 : 1 // 255 is low
	def batteryLevel = (val == 0) ? "normal" : "low"
	
	return [
		name: "battery", 
		value: batteryVal,
		unit: "%", 
		descriptionText: "$batteryLevel", 
		isStateChange: true,
		displayed: (batteryLevel == "low")
	]
}

def zwaveEvent(hubitat.zwave.Command cmd) {
	log.warn "Unexpected Command: $cmd"
	return []
}

private batteryHealthGetCmd() {
	return configGetCmd(42)
}

private deviceNotifyTypeSetCmd(useBasicReport) {
	return configSetCmd(80, (useBasicReport ? 2 : 0))
}

private deviceNotifyTypeGetCmd() {
	return configGetCmd(80)
}

private sendLowBatterySetCmd() {
	return configSetCmd(81, 1)
}

private repeatGetCmd() {
	return configGetCmd(2)
}

private repeatSetCmd(repeat) {
	repeat = validateRange(repeat, 1, 1, 300, "Repeat")
	return configSetCmd(2, repeat)
}

private doorbellGetCmd() {
	return configGetCmd(5)
}

private doorbellSetCmd(track) {
	track = validateTrack(track)
	return configSetCmd(5, track)
}

private volumeGetCmd() {
	return configGetCmd(8)
}

private volumeSetCmd(volume) {
	volume = validateRange(volume, 5, 0, 10, "Volume")
	return configSetCmd(8, volume)
}

private playTrackCmds(track) {
	track = validateTrack(track)
	def cmds = []
	
	if (track != 0) {	
		sendEvent(getStatusEventMap("Playing Track #${track}"))
	
		// Disable Notifications
		cmds << deviceNotifyTypeSetCmd(false)
		cmds << "delay 500"
	}
	
	cmds += [
		playTrackSetCmd(track),
		"delay 500",
		deviceNotifyTypeSetCmd(true)
	]
}

private playTrackSetCmd(track) {
	return configSetCmd(6, validateTrack(track))
}

private configGetCmd(int paramNum) {
	return secureCmd(zwave.configurationV2.configurationGet(parameterNumber: paramNum))
}

private configSetCmd(int paramNum, int val) {
	return secureCmd(zwave.configurationV2.configurationSet(parameterNumber: paramNum, size: 1, configurationValue: [val]))
}

private secureCmd(cmd) {
	if (getDataValue("zwaveSecurePairingComplete") == "true") {
		return zwave.securityV1.securityMessageEncapsulation().encapsulate(cmd).format()
	}
	else {
		return cmd.format()
	}	
}


def logsOff(){
	log.warn "debug logging disabled..."
	updateLogSettings(false)	
}

private updateLogSettings(value) {
	updateSetting("logEnable", value, "bool")
	updateSetting("txtEnable", value, "bool")	
}

private updateSetting(name, value, type) {
	try {
		if (settings && settings["$name"]) {
			device.updateSetting("$name", [value:"$value", type:"$type"])
		}
	}
	catch (e) {
		log.warn "Unable to set the value of setting $name to $value"
	}
}

private getCheckinIntervalSettingMinutes() {
	return convertOptionSettingToInt(checkinIntervalOptions, checkinIntervalSetting)
}

private getCheckinIntervalSetting() {
	return settings?.checkinInterval ?: "12 Hours"
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
		[name: "12 Hours", value: 720],
		[name: "18 Hours", value: 1080],
		[name: "24 Hours", value: 1440]
	]
}

private convertOptionSettingToInt(options, settingVal) {
	return safeToInt(options?.find { "${settingVal}" == it.name }?.value, 0)
}

private safeToInt(val, defaultVal=-1) {
	return "${val}"?.isInteger() ? "${val}".toInteger() : defaultVal
}

private int validateTrack(track) {
	validateRange(track, 1, 0, 100, "Track")
}

private int validateRange(val, defaultVal, minVal, maxVal, desc) {
	def result
	def errorType = null
	if (isInt(val)) {
		result = safeToInt(val)
	}
	else {
		errorType = "invalid"
		result = defaultVal
	}
	
	if (result > maxVal) {
		errorType = "too high"
		result = maxVal
	} else if (result < minVal) {
		errorType = "too low"
		result = minVal
	} 

	if (errorType) {
		logDebug "$desc: $val is $errorType, using $result instead."
	}
	return result
}

private isInt(val) {
	return val?.toString()?.isInteger() ? true : false
}

private getEventMap(name, val, displayed=true, desc="") {
	desc = desc ?: "${device.displayName}: ${name} is ${val}"
	if (displayed) {
		logDesc(desc)
	}
	return [
		name: name, 
		value: val,
		descriptionText: desc,
		isStateChange: true
	]
}

private isDuplicateCommand(lastExecuted, allowedMil) {
	!lastExecuted ? false : (lastExecuted + allowedMil > new Date().time) 
}

private logDebug(msg) {
	if (settings?.logEnable) log.debug msg
}

private logDesc(msg) {
	if (settings?.txtEnable) log.info msg
}

private logTrace(msg) {
	 // log.trace msg
}
