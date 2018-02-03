/**
 *  Aeon Labs Siren v 1.1       
 *      (Model:ZW080-A17)
 *
 *	Changelog:
 *
 *	1.1 (08/01/2017)
 *		- Fixed fingerprint, set default values, and removed code that's not needed for hubitat.
 *
 *	1.0.3 (07/30/2017)
 *		-	Initial Release
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
 */
metadata {
	definition (name: "Aeon Labs Siren", namespace: "krlaframboise", author: "Kevin LaFramboise") {
		capability "Configuration"
		capability "Actuator"
		capability "Switch"
		capability "Alarm"
		capability "Tone"
		capability "Speech Synthesis"
	
		attribute "status", "string"		
		attribute "lastCheckin", "string"
		
		command "shortBeep"
		command "shortBeep3x"
		command "longBeep"
		command "longBeep3x"
		
		fingerprint deviceId: "0050", inClusters: "0x5E,0x25,0x70,0x85,0x59,0x72,0x2B,0x2C,0x86,0x7A,0x73,0x98", outClusters: "0x5A,0x82", mfr:"0086", prod:"0104", deviceJoinName: "Aeon Labs Siren"
	}

	preferences {
		input "sirenSound", "number", 
			title: "Siren Sound (1-5)", 
			range: "1..5",
			defaultValue: 5
		input "sirenVolume", "number", 
			title: "Siren Volume (1-3)",
			range: "1..3",
			defaultValue: 1
		input "alarmDuration", "number", 
			title: "Turn siren off after: (seconds)\n(0=unlimited)",
			defaultValue: 0
		input "beepSound", "number", 
			title: "Beep Sound (1-5)",
			range: "1..5",
			defaultValue: 3
		input "beepVolume", "number", 
			title: "Beep Volume (1-3)", 
			range: "1..3",
			defaultValue: 1
		input "beepRepeat", "number", 
			title: "Beep Repeat (1-100)", 
			range: "1..100",
			defaultValue: 1
		input "beepRepeatDelay", "number", 
			title: "Time Between Beeps in Milliseconds",
			defaultValue: 1000
		input "beepLength", "number", 
			title: "Length of Beep in Milliseconds",
			defaultValue: 50
		input "beepEvery", "number", 
			title: "Scheduled Beep Every (seconds)",
			defaultValue: 10
		input "beepStopAfter", "number", 
			title: "Stop Scheduled Beep After (seconds)",
			defaultValue: 60
		input "useBeepScheduleForBeep", "bool",
			title: "Play Beep Schedule for Beep Command?",
			defaultValue: false		
		input "useBeepDelayedAlarm", "bool",
			title: "Play Beep Schedule Before Sounding Alarm?",
			defaultValue: false		
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

def updated() {
		logInfo "updated..."
		log.warn "debug logging is: ${logEnable == true}"
		log.warn "description logging is: ${txtEnable == true}"
	
	startHealthPollSchedule()
	
	def cmds = []
	if (!state.isConfigured) {
		cmds += configure()
	}
	return cmds
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
	logTrace "healthPoll()"	
	def result = []
	result << versionGetCmd()
	return result
}

def configure() {
	log.warn "configure..."
	state.isConfigured = true
	
	saveSpeakCommandList()

	runIn(1800, logsOff)
		
	def cmds = [
		sendNotificationsSetCmd(),
		switchGetCmd()
	]
	return delayBetween(cmds, 1000)
}

private saveSpeakCommandList() {
	if (!state.SPEAK_BasicCommands) {	
		state.SPEAK_BasicCommands = "on | off | siren | strobe | both | beep | shortBeep | longBeep | shortBeep3x | longBeep3x | startBeep | startBeepDelayedAlarm"
		state.SPEAK_CustomAlarm = "customAlarm sound# volume# durationSeconds#"
		state.SPEAK_DelayedAlarm = "delayedAlarm sound# volume# durationSeconds# delaySeconds#"
		state.SPEAK_CustomBeep = "customBeep sound# volume#"
		state.SPEAK_CustomBeepAdvanced = "customBeep sound# volume# repeat# repeatDelayMilliseconds# beepLengthMilliseconds#"
		state.SPEAK_StartCustomBeep = "startCustomBeep beepEverySeconds# stopAfterSeconds# sound# volume#"
		state.SPEAK_StartCustomBeepAdvanced = "startCustomBeep beepEverySeconds# stopAfterSeconds# sound# volume# repeat# #repeatDelayMilliseconds# beepLengthMilliseconds#"
	}
}


def strobe() {
	logDebug "strobe()"
	turnOn()
}

def siren() {
	logDebug "siren()"
	turnOn()
}

def both() {
	logDebug "both()"
	turnOn()
}

def on() {
	logDebug "on()"
	turnOn()
}

private turnOn() {
	logTrace "turnOn()"
	if (settings.useBeepDelayedAlarm) {
		startBeepDelayedAlarm()
	}
	else {
		changeStatus("alarm")
		playDefaultAlarm()
	}
}

def off() {
	logDebug "off()"
	changeStatus("off")
	turnOff()
}

private turnOff() {	
	logTrace "turnOff()"
	delayBetween([
		switchOffSetCmd(),
		switchGetCmd()
	], 100)
}

def speak(msg) {
	logDebug "speak($msg)"
	msg = cleanSpeakMsg(msg)
	def msgCmd = extractSpeakMsgCmd(msg)
	def args = extractSpeakMsgArgs(msg)
	
	def cmds = []
	switch (msgCmd) {
		case ["siren", "strobe", "both", "on"]:
			cmds = both()
			break
		case ["off"]:
			cmds = off()
			break
		case "beep":
			cmds = beep()
			break
		case "shortbeep":
			cmds = shortBeep(args[0])
			break
		case "shortbeep3x":
			cmds = shortBeep3x(args[0])
			break
		case "longbeep":
			cmds = longBeep(args[0])
			break
		case "longbeep3x":
			cmds = longBeep3x(args[0])
			break
		case "startbeep":
			cmds = startBeep()
			break
		case "startbeepdelayedalarm":
			cmds = startBeepDelayedAlarm()
			break
		case "customalarm":
			cmds customAlarm(args[0], args[1], args[2])
			break
		case "delayedalarm":
			cmds = delayedAlarm(args[0], args[1], args[2], args[3])
			break
		case "custombeep":
			cmds = customBeep(args[0], args[1], args[2], args[3], args[4])
			break
		case "startcustombeep":
			cmds = startCustomBeep(args[0], args[1], args[2], args[3], args[4], args[5], args[6])
			break
		default:
			cmds = []
	}
	if (!cmds && msgCmd != "delayedalarm") {
		logDebug "'$msg' is not a valid speak command."
	}
	return cmds
}

private cleanSpeakMsg(msg) {
	msg = msg ? msg.toLowerCase().trim() : ""
	return "$msg".
		replace(",", " ").		
		replace("(", "").
		replace(")", "").
		replace("  ", " ").
		trim()		
}

private extractSpeakMsgCmd(msg) {
	def index = msg?.indexOf(" ")
	return (index > 0) ? msg.substring(0, index).trim() : msg
}

private extractSpeakMsgArgs(msg) {
	def args = []
	def index = msg?.indexOf(" ")
	if (index > 0) {
		msg = msg.substring(index).trim()
		msg.split(" ").each {
			args << it
		}
	}
	for (i = args.size(); i < 7; i+=1) {
		args << null
	}
	return args
}

// Repeatedly plays the default beep based on the beepEvery and beepStopAfter settings and then turns on alarm.
def startBeepDelayedAlarm() {
	logDebug "startBeepDelayedAlarm()..."
	changeStatus("beepDelayedAlarm")
	return startDefaultBeepSchedule()	
}

def playPendingAlarm() {
	logTrace "playPendingAlarm()..."
	state.alarmPending = false
	def result = []
	if (state.scheduledAlarm) {
		def sound = state.scheduledAlarm?.sound
		def volume = state.scheduledAlarm?.volume
		def duration = state.scheduledAlarm?.duration
		state.scheduledAlarm = null		
		result += customAlarm(sound, volume, duration)
	}
	else {
		result += playDefaultAlarm()
	}	
	return result
}

private playDefaultAlarm() {
	logTrace "playDefaultAlarm()..."
	return playAlarm(settings.sirenSound, settings.sirenVolume, settings.alarmDuration)
}

def customAlarm(sound, volume, duration) {
	logDebug "customAlarm($sound, $volume, $duration)..."
	changeStatus("customAlarm")
	return playAlarm(sound, volume, duration)
}

private playAlarm(sound, volume, duration) {
	logTrace "playAlarm($sound, $volume, $duration)..."
	
	def durationMsg = (duration && durantion instanceof Integer && (int)duration > 0) ? ", duration: $duration" : ""
	logDebug "Sounding Alarm: [sound: $sound, volume: $volume$durationMsg]"
	
	sound = validateSound(sound)
	volume = validateVolume(volume)
	duration = validateRange(duration, 0, 0, Integer.MAX_VALUE, "Alarm Duration")

	if (currentStatus() in ["alarm", "delayedAlarm", "beepDelayedAlarm"]) {
		sendEvent(getEventMap("alarm", "both"))
		sendEvent(getEventMap("switch", "on", false))
	}
	
	def cmds = []
	cmds << sirenSoundVolumeSetCmd(sound, volume)
	
	if (duration > 0) {
		cmds << "delay ${duration * 1000}"
		cmds += turnOff()
	}

	return cmds	
}

def delayedAlarm(sound, volume, duration, delay) {
	logDebug "delayedAlarm($sound, $volume, $duration, $delay)"
	changeStatus("delayedAlarm")
	return startDelayedAlarm(sound, volume, duration, delay)	
}

private startDelayedAlarm(sound, volume, duration, delay) {
	logTrace "startDelayedAlarm($sound, $volume, $duration, $delay)"
	
	state.scheduledAlarm = [
		"sound": sound,
		"volume": volume,
		"duration": duration
	]
	
	delay = validateRange(delay, 3, 1, Integer.MAX_VALUE, "delay")
	
	logDebug "Starting ${currentStatus()} [sound: $sound, volume: $volume, duration: $duration, delay: $delay]"
	
	def result = []
	runIn(delay, playPendingAlarm)
	return result
}

def beep() {
	logDebug "beep()"
	if (!settings.useBeepScheduleForBeep) {
		changeStatus("beep")
		return playDefaultBeep()	
	}
	else {
		return startBeep()
	}
}

private playDefaultBeep() {
	logTrace "playDefaultBeep()"
	return playBeep(
		settings.beepSound,
		settings.beepVolume,
		settings.beepRepeat,
		settings.beepRepeatDelay,
		settings.beepLength
	)
}

def shortBeep(volume=null) {
	return customBeep(3, volume, 1, 0, 50)
}

def longBeep(volume=null) {
	return customBeep(3, volume, 1, 0, 250)
}

def shortBeep3x(volume=null) {
	return customBeep(3, volume, 3, 25, 50)
}

def longBeep3x(volume=null) {
	return customBeep(3, volume, 3, 150, 200)
}

def startBeep() {
	logTrace "startBeep()"
	changeStatus("beepSchedule")
	return startDefaultBeepSchedule()	
}

private startDefaultBeepSchedule() {
	logTrace "startBeepSchedule()"
	return startBeepSchedule(
		settings.beepEvery,
		settings.beepStopAfter,
		settings.beepSound,
		settings.beepVolume,
		settings.beepRepeat,
		settings.beepRepeatDelay,
		settings.beepLength
	)
}

def startCustomBeep(beepEverySeconds, stopAfterSeconds, sound, volume, repeat=1, repeatDelayMS=1000, beepLengthMS=100) {	
	logTrace "startCustomBeep($beepEverySeconds, $stopAfterSeconds, $sound, $volume, $repeat, $repeatDelayMS, $beepLengthMS)"
	changeStatus("customBeepSchedule")
	
	return startBeepSchedule(beepEverySeconds, stopAfterSeconds, sound, volume, repeat, repeatDelayMS, beepLengthMS)	
}

private startBeepSchedule(beepEverySeconds, stopAfterSeconds, sound, volume, repeat, repeatDelayMS, beepLengthMS) {
	logTrace "startBeepSchedule($beepEverySeconds, $stopAfterSeconds, $sound, $volume, $repeat, $repeatDelayMS, $beepLengthMS)"
		
	state.beepSchedule = [
		"startTime": (new Date().time),
		"beepEvery": validateBeepEvery(beepEverySeconds),
		"stopAfter": validateBeepStopAfter(stopAfterSeconds),
		"sound": sound,
		"volume": volume,
		"repeat": repeat,
		"repeatDelay": repeatDelayMS,
		"beepLength": beepLengthMS
	]

	return playScheduledBeep()
}

private playScheduledBeep() {
	logTrace "playScheduledBeep()"
	def beepSchedule = state.beepSchedule
	
	def cmds = []
	if (beepScheduleStillActive(beepSchedule?.startTime, beepSchedule?.stopAfter)) {
		cmds += playBeep(
			beepSchedule.sound,
			beepSchedule.volume,
			beepSchedule.repeat,
			beepSchedule.repeatDelay,
			beepSchedule.beepLength
		)
	}

	if (nextScheduledBeepStillActive()) {		
		if (beepSchedule.beepEvery > 0) {
			runIn((beepSchedule.beepEvery), playNextBeep)
		}		
	}
	else {		
		state.beepSchedule = null
		state.beepScheduleRunning = false
		
		if (state.alarmPending) {		
			cmds += playPendingAlarm()
		}
		else {
			cmds += turnOff()
		}
	}	
	return cmds
}


def playNextBeep() {
	logDebug "playNextBeep()"
	def cmds = []
	cmds += playScheduledBeep()
	return cmds
}


private nextScheduledBeepStillActive() {	
	def sched = state.beepSchedule
	
	if (sched?.beepEvery != null) {	
		def adjustedStartTime = (sched.startTime - (sched.beepEvery * 1000))
		return beepScheduleStillActive(adjustedStartTime, sched.stopAfter)
	} 
	else {		
		return false
	}
}

private beepScheduleStillActive(startTime, stopAfter) {
	if (startTime && stopAfter) {		
		def endTimeMS = startTime + (stopAfter * 1000)
		return (new Date().time < endTimeMS) && state.beepScheduleRunning
	}
	else {		
		return false
	}
}

def customBeep(sound, volume, repeat=1, repeatDelayMS=1000, beepLengthMS=100) {
	if (!volume) {
		volume = settings.beepVolume
	}
	
	logTrace "customBeep($sound, $volume, $repeat, $repeatDelayMS, $beepLengthMS)"
	changeStatus("customBeep")
	return playBeep(sound, volume, repeat, repeatDelayMS, beepLengthMS)
}

private playBeep(sound, volume, repeat, repeatDelayMS, beepLengthMS) {
	logTrace "playBeep($sound, $volume, $repeat, $repeatDelayMS, $beepLengthMS)"
	
	int maxMS = 18000
	sound = validateSound(sound, 3)
	volume = validateVolume(volume)
	beepLengthMS = validateRange(beepLengthMS, 50, 0, maxMS, "Beep Length")
	repeatDelayMS = validateRepeatDelay(repeatDelayMS, beepLengthMS, maxMS)
	repeat = validateRepeat(repeat, beepLengthMS, repeatDelayMS, maxMS)

	def cmds = []
	for (int repeatIndex = 1; repeatIndex <= repeat; repeatIndex++) {	
		cmds << sirenSoundVolumeSetCmd(sound, volume)
		
		if (beepLengthMS > 0) {
			cmds << "delay $beepLengthMS"
		}
		
		cmds << switchOffSetCmd()
		
		if (repeat > 1 && repeatDelayMS > 0) {
			cmds << "delay $repeatDelayMS"
		}
	}

	if (!state.beepScheduleRunning && !state.alarmPending && currentStatus() != "off") {
		cmds << turnOff()
	}	
	return cmds
}

private changeStatus(newStatus) {
	def oldStatus = currentStatus()

	finalizeOldStatus(oldStatus, newStatus)
	
	if (newStatus in ["delayedAlarm", "beepDelayedAlarm"]) {
		state.alarmPending = true
	}
	
	if (newStatus in ["beepDelayedAlarm", "beepSchedule", "customBeepSchedule"]) {
		state.beepScheduleRunning = true
	}
	
	def displayStatus = (
		oldStatus != newStatus && 
		newStatus != "alarm" && 
		!(oldStatus in ["alarm", "delayedAlarm", "beepDelayedAlarm"]))
	
	sendEvent(getEventMap("status", newStatus, displayStatus))
}

private finalizeOldStatus(oldStatus, newStatus) {
	if (state.alarmPending && 
	oldStatus in ["delayedAlarm", "beepDelayedAlarm"] &&
	!(newStatus in ["alarm", "customAlarm"])) {
		logDebug "Delayed Alarm Cancelled"			
	}
	else if (state.beepScheduleRunning) {
		if (nextScheduledBeepStillActive()) {
			logDebug "Beep Schedule Cancelled"
		}
		else {
			logDebug "Beep Schedule Completed"
		}
	}	
	state.alarmPending = false
	state.beepScheduleRunning = false		
	state.scheduledAlarm = null
	state.beepSchedule = null
}

def parse(String description) {
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
		return dt.format("MM/dd/yyyy hh:mm:ss a", TimeZone.getTimeZone(timeZoneId))
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
			log.warn "Unable to encapsulate: $cmd"
		}
	}
	return result
}

def zwaveEvent(hubitat.zwave.commands.versionv1.VersionReport cmd) {
	logTrace "VersionReport"
	// VersionReport not working
	return []
}

def zwaveEvent(hubitat.zwave.commands.manufacturerspecificv2.ManufacturerSpecificReport cmd) {
	logTrace "ManufacturerSpecificReport"
	return []
}


def zwaveEvent(hubitat.zwave.commands.switchbinaryv1.SwitchBinaryReport cmd) {
	logTrace "SwitchBinaryReport[value: $cmd.value]"
	def result = []
	if (cmd.value == 0) {		
		changeStatus("off")
				
		def alarmDisplayed = (device.currentValue("alarm") == "both")	
		if (alarmDisplayed) {
			logDebug "Alarm is off"
		}
		
		result << createEvent(getEventMap("alarm", "off", alarmDisplayed))
			
		result << createEvent(getEventMap("switch", "off", false))
	}
	return result
}

def zwaveEvent(hubitat.zwave.commands.basicv1.BasicReport cmd) {
	logTrace "BasicReport: ${cmd.value}"
	return []
}

private isDuplicateCommand(lastExecuted, allowedMil) {
	!lastExecuted ? false : (lastExecuted + allowedMil > new Date().time) 
}

def zwaveEvent(hubitat.zwave.commands.configurationv1.ConfigurationReport cmd) {
	logTrace "ConfigurationReport[parameterNumber: $cmd.parameterNumber, configurationValue: $cmd.configurationValue]"
	return []
}

def zwaveEvent(hubitat.zwave.Command cmd) {
	log.warn "Unexpected Command: $cmd"
	return []
}

private sirenSoundVolumeSetCmd(int sound, int volume) {	
	return configSetCmd(37, 2, [validateSound(sound), validateVolume(volume)])
}
private sirenSoundVolumeGetCmd() {
	return configGetCmd(37)
}

private sendNotificationsSetCmd() {
	return configSetCmd(80, 1, [0])
}
private sendNotificationsGetCmd() {
	return configGetCmd(80)
}

private configSetCmd(paramNumber, valSize, val) {	
	return secureCmd(zwave.configurationV1.configurationSet(parameterNumber: paramNumber, size: valSize, configurationValue: val))
}

private configGetCmd(paramNumber) {	
	return secureCmd(zwave.configurationV1.configurationGet(parameterNumber: paramNumber))
}

private switchOffSetCmd() {
	return secureCmd(zwave.switchBinaryV1.switchBinarySet(switchValue: 0))
}

private switchGetCmd() {
	return secureCmd(zwave.switchBinaryV1.switchBinaryGet())
}

private basicGetCmd() {
	return secureCmd(zwave.basicV1.basicGet())
}

private versionGetCmd() {
	// return secureCmd(zwave.versionV1.versionGet())
	// Using ManufacturerSpecificGet because Version Reporting is broken.
	return manufacturerSpecificGetCmd()
}

private manufacturerSpecificGetCmd() {
	return secureCmd(zwave.manufacturerSpecificV2.manufacturerSpecificGet())
}

private secureCmd(cmd) {
	if (zwaveInfo?.zw?.contains("s") || ("0x98" in device.rawDescription?.split(" "))) {
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
		0x2B: 1,	// Scene Activation 
		0x2C: 1,	// Scene Actuator Conf 
		0x59: 1,  // AssociationGrpInfo
		0x5E: 2,  // ZwaveplusInfo
		0x70: 1,  // Configuration
		0x72: 2,  // ManufacturerSpecific
		0x73: 1,  // Powerlevel
		0x7A: 2,	// Firmware Update Md
		0x85: 2,  // Association
		0x86: 1,	// Version (2)
		0x98: 1		// Security
	]
}

private getCheckinIntervalSettingMinutes() {
	return convertOptionSettingToInt(checkinIntervalOptions, checkinIntervalSetting)
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
		[name: "12 Hours   (Default)", value: 720],
		[name: "18 Hours", value: 1080],
		[name: "24 Hours", value: 1440]
	]
}

private convertOptionSettingToInt(options, settingVal) {
	return safeToInt(options?.find { "${settingVal}" == it.name }?.value, 0)
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

private int validateSound(sound, int defaultSound=1) {
	return validateRange(sound, defaultSound, 1, 5, "Sound")
}

private int validateVolume(volume, int defaultVolume=1) {
	return validateRange(volume, defaultVolume, 1, 3, "Volume")
}

private int validateRepeatDelay(repeatDelayMS, int beepLengthMS, int maxMS) {
	int repeatDelayMaxMS = (beepLengthMS == maxMS) ? 0 : (maxMS - beepLengthMS)
	return validateRange(repeatDelayMS, 1000, 0, repeatDelayMaxMS, "Repeat Delay")
}

private int validateRepeat(repeat, int beepLengthMS, int repeatDelayMS, int maxMS) {
	int combinedMS = (beepLengthMS + repeatDelayMS)
	int maxRepeat = (combinedMS >= maxMS) ? 0 : (maxMS / combinedMS).toInteger()
	return validateRange(repeat, 1, 0, maxRepeat, "Repeat")
}

private int validateBeepEvery(seconds) {
	validateRange(seconds, 10, 0, Integer.MAX_VALUE, "Beep Every")
}

private int validateBeepStopAfter(seconds) {
	validateRange(seconds, 60, 2, Integer.MAX_VALUE, "Beep Stop After")
}

private int validateRange(val, defaultVal, minVal, maxVal, desc) {
	def result
	def errorType = null
	if (isInt(val)) {
		result = val.toString().toInteger()
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
		logDebug("$desc: $val is $errorType, using $result instead.")
	}
	return result
}

private isInt(val) {
	return val?.toString()?.isInteger() ? true : false
}

private currentStatus() {
	return device.currentValue("status") ?: ""
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


def logsOff(){
	log.warn "debug logging disabled..."
	device.updateSetting("logEnable",[value:"false",type:"bool"])
}	

private logDebug(msg) {
	if (logEnable) log.debug msg
}

private logDesc(msg) {
	if (txtEnable) log.info msg
}

private logInfo(msg) {
	log.info msg
}

private logTrace(msg) {
	// log.trace msg
}