/*
 *  Zooz Garage Door v1.0.1	(Drivers Code)
 *
 *
 *  Changelog:
 *
 *    1.0.1 (05/09/2020)
 *      - Added Import Url
 *
 *    1.0 (04/11/2020)
 *      - Initial Release
 *
 *
 *  Copyright 2020 Zooz
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

metadata {
	definition (
		name: "Zooz Garage Door",
		namespace: "Zooz",
		author: "Kevin LaFramboise (@krlaframboise)",
		importUrl: "https://raw.githubusercontent.com/krlaframboise/Hubitat/master/drivers/zooz/zooz-garage-door.src/zooz-garage-door.groovy"
	) {
		capability "Actuator"
		capability "Sensor"
		capability "Contact Sensor"
		capability "Door Control"
        capability "Garage Door Control"
		capability "Refresh"
	}

	preferences {
		input "debugLogging", "bool", 
			title: "Enable debug logging?",
			defaultValue: true, 
			required: false
	}
}


def installed() {
	initialize()
}

def updated() {
	logDebug "updated()..."
	initialize()
}

private initialize() {
	if (!device.currentValue("door")) {
		sendEvent(name: "door", value: "open")
	}
	if (!device.currentValue("contact")) {
		sendEvent(name: "contact", value: "open")
	}
}


def parse(String description) {
	logDebug "parse(description)..."
}

def parse(Map evt) {
	if (evt) {
		evt.descriptionText = evt.descriptionText ?: "${device.displayName} - ${evt.name} is ${evt.value}"
		
		if (evt.displayed != false) {
			logDebug "${evt.descriptionText}"
		}		
		sendEvent(evt)
	}
}


void open() {
	logDebug "open()..."
	parent.childOpen(device.deviceNetworkId)
}


void close() {
	logDebug "close()..."
	parent.childClose(device.deviceNetworkId)
}

void refresh() {
	logDebug "refresh()..."
	parent.childRefresh(device.deviceNetworkId)
}


void logDebug(msg) {
	if (settings?.debugLogging != false) {
		log.debug "$msg"
	}
}
