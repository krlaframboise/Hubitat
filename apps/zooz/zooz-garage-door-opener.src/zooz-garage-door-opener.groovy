/*
 *  Zooz Garage Door Opener v1.0	(Apps Code)
 *
 *
 * WARNING: Using a homemade garage door opener can be dangerous so use this code at your own risk. 
 *
 *  Changelog:
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

definition(
	name: "Zooz Garage Door Opener",
    namespace: "Zooz",
    author: "Kevin LaFramboise (@krlaframboise)",
    description: "Control your garage doors with the Zooz MultiRelay device and any open/close sensor.",
	singleInstance: true,	
	category: "Convenience",
    iconUrl: "https://raw.githubusercontent.com/krlaframboise/Resources/master/Zooz/garage.png",
    iconX2Url: "https://raw.githubusercontent.com/krlaframboise/Resources/master/Zooz/garage.png",
    iconX3Url: "https://raw.githubusercontent.com/krlaframboise/Resources/master/Zooz/garage.png",
	installOnOpen: true
)


preferences {
	page(name:"pageMain")
	page(name:"pageRemove")
}

def pageMain() {	
	dynamicPage(name: "pageMain", title: "", install: true, uninstall: false) {
		section() {
			paragraph "Created for use with the Zooz MultiRelay ZEN16 available from <a href = 'https://www.thesmartesthouse.com/products/zooz-z-wave-plus-s2-multirelay-zen16-with-3-dry-contact-relays-20-a-15-a-15-a' target='_blank'>www.thesmartesthouse.com</a>"
			paragraph "You'll also need a wireless open/close sensor on the garage door to use this app."
			paragraph "It's recommend to use an audio notification (a loud siren) whenever the garage door is opened or closed remotely."
			paragraph "For help and support go to <a href='https://www.support.getzooz.com' target='_blank'>www.support.getzooz.com</a>"
		}
		section() {
			app(name: "garageDoors", title: "Create a Garage Door", appName: "Zooz Garage Door Opener App", namespace: "Zooz", multiple: true, uninstall: false)
		}
		
		if (state.installed) {
			section() {
				href "pageRemove", title: "Remove All Garage Doors", description: ""
			}
		}
	}	
}


def pageRemove() {
	dynamicPage(name: "pageRemove", title: "", install: false, uninstall: true) {
		section() {			
			paragraph "<b>WARNING:</b> You are about to remove the Zooz Garage Door Opener App and ALL of the Garage Door devices it created.", required: true, state: null
		}
	}
}


def installed() {
	log.warn "installed()..."
	state.installed = true
}

def updated() {		
	log.warn "updated()..."
}
