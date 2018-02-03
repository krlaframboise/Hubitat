/**
 *  HUBITAT: Other Hub Event Pusher 0.1 (BETA)
 *
 *  Author: 
 *    Kevin LaFramboise (krlaframboise)
 *
 *  Changelog:
 *
 *    1.0 (09/05/2017)
 *			- Beta Release
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
definition(
    name: "Other Hub Event Pusher",
    namespace: "krlaframboise",
    author: "Kevin LaFramboise",
    description: "Pushes events to external url",
    category: "Convenience",
    iconUrl: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience.png",
    iconX2Url: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience@2x.png",
    iconX3Url: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience@2x.png"
)

preferences {
	page(name: "main")
}

def main(){
	return (
		dynamicPage(name: "main", title: "Other Hub Event Pusher", uninstall: true, install: true){
			section("") {
				paragraph "If you open the Dashboard in the SmartThings SmartApp Other Hub Device Viewer Dashboard, the url needed for the field below is shown in the textbox at the bottom of the page."
				input "smartThingsUrl", "text", title:"SmartThings Other Hub Device Viewer Dashboard Url:", required: true
			}
			section("Push Events for These Devices") {
				input "switchDevices", "capability.switch", title: "Switches", required:false, multiple: true				
				input "motionDevices", "capability.motionSensor", title: "Motion Sensors", required:false, multiple: true
				input "contactDevices", "capability.contactSensor", title: "Contact Sensors", required:false, multiple: true
			}
			section("Other Options") {
				input "eventPushEnabled", "bool", 
					title: "Event Push Enabled?", 
					defaultValue: true, 
					required: false
				input "debugLogging", "bool", 
					title: "Enable debug logging?", 
					defaultValue: true, 
					required: false
			}
		}
	)
}

def installed() {
	logDebug "installed()..."
	updated()
}


def updated() {
	logDebug "updated()..."
	unsubscribe()
	initialize()
}


def initialize() {
	logDebug "initialize()"
	if (eventPushEnabled) {
		subscribe(motionDevices, "motion", handleDeviceEvent)
		subscribe(contactDevices, "contact", handleDeviceEvent)
		subscribe(switchDevices, "switch", handleDeviceEvent)
	}
	else {
		log.warn "Event Push is Disabled."
	}
}

def handleDeviceEvent(evt) {
	// logDebug "handleDeviceEvent: ${evt.device?.displayName} ${evt.name} ${evt.value}"
			
	def uri = smartThingsUri
	def path = "${smartThingsRelativePath}/event/${evt.name}/${evt.value}/${evt.device.id}"

	if (uri) {
		def params = [
			uri: "${uri}",
			path: "${path}"
		]
		
		def msg = "Pushing ${evt.name} ${evt.value} to ${evt.device?.displayName}(${evt.device?.id})"
		try {
			httpGet(params) { resp ->
				logDebug "${msg} Response: ${resp?.status}"
			}
		} catch (e) {
			log.error "${msg} Error: $e"
		}
	}	
	else {
		log.warn "${smartThingsUrl} is not a valid SmartThings Url."
	}
}

private getSmartThingsUri() {
	return urlRemoveAfterAtText(smartThingsUrl, "/api/token/")
}

private getSmartThingsRelativePath() {
	return urlRemoveAfterAtText(smartThingsUrl?.replace(smartThingsUri, ""), "/dashboard/")
}

private urlRemoveAfterAtText(url, text) {
	def i = url?.indexOf("$text")
  if (i && i > 1) {
		def removed = url.substring(i)
		return url.replace("$removed", "")
  }
	else {
		return url
	}
}

private logDebug(msg) {
	if (settings?.traceLogging != false) {
		log.debug "$msg"
	}
}
