/*
 *  Zooz Garage Door Controller v1.0	(Apps Code)
 *
 *
 * WARNING: Using a homemade garage door opener can be dangerous so use this code at your own risk. 
 *
 *  Changelog:
 *
 *    1.0 (04/08/2020)
 *      - Initial Release
 *
 *
 *	Copyright 2020 Kevin LaFramboise (@krlaframboise)
 *
 *  Licensed under the Apache License, Version 2.0 (the
 *  "License"); you may not use this file except in
 *  compliance with the License. You may obtain a copy of
 *  the License at:
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in
 *  writing, software distributed under the License is
 *  distributed on an "AS IS" BASIS, WITHOUT WARRANTIES
 *  OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing
 *  permissions and limitations under the License.
 *
 */

definition(
	name: "Zooz Garage Door Controller",
    namespace: "krlaframboise",
    author: "Kevin LaFramboise",
    description: "Control your garage doors with the Zooz MultiRelay device and any open/close sensor.",
	singleInstance: true,	
	category: "Convenience",
    iconUrl: "http://cdn.device-icons.smartthings.com/Transportation/transportation12-icn.png",
    iconX2Url: "http://cdn.device-icons.smartthings.com/Transportation/transportation12-icn@2x.png",
    iconX3Url: "http://cdn.device-icons.smartthings.com/Transportation/transportation12-icn@3x.png",
	installOnOpen: true
)


preferences {
	page(name:"pageMain")
	page(name:"pageRemove")
}

def pageMain() {	
	dynamicPage(name: "pageMain", title: "", install: true, uninstall: false) {
		section() {
			app(name: "garageDoors", title: "Tap to Add a Garage Door", appName: "Zooz Garage Door Controller App", namespace: "krlaframboise", multiple: true, uninstall: false)
		}
		
		if (state.installed) {
			section() {
				href "pageRemove", title: "", description: "Remove All Garage Doors"
			}
		}
	}	
}


def pageRemove() {
	dynamicPage(name: "pageRemove", title: "", install: false, uninstall: true) {
		section() {			
			paragraph "WARNING: You are about to remove the Zooz Garage Door Controller App and ALL of the Garage Door Opener devices it created.", required: true, state: null
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
