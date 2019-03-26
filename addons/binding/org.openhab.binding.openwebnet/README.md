# OpenWebNet (BTicino/Legrand) Binding

This new binding integrates BTicino / Legrand MyHOME(r) BUS & ZigBee wireless (MyHOME_Play) devices using the **[OpenWebNet](https://en.wikipedia.org/wiki/OpenWebNet) protocol**.
It is the first known binding for openHAB 2 that **supports *both* wired BUS/SCS** as well as **wireless setups**, all in the same biding. The two networks can be configured simultaneously.
It's also the first OpenWebNet binding with support for discovery of BUS/SCS gateways and devices.
Commands from openHAB and feedback (events) from BUS/SCS and wireless network are supported.
Support for both numeric (`12345`) and alpha-numeric (`abcde` - HMAC authentication) gateway passwords is included.

## Prerequisites

In order for this biding to work, a **BTicino/Legrand OpenWebNet gateway** is needed in your home system to talk to devices.
These gateways have been tested with the binding:

- **IP gateways** or scenario programmers, such as BTicino 
[F454](http://www.homesystems-legrandgroup.com/BtHomeSystems/productDetail.action?productId=006), 
[MyHOMEServer1](http://www.bticino.com/products-catalogue/myhome_up-simple-home-automation-system/), 
[MyHOME_Screen10](http://www.homesystems-legrandgroup.com/BtHomeSystems/productDetail.action?lang=EN&productId=001), 
[MH202](http://www.homesystems-legrandgroup.com/BtHomeSystems/productDetail.action?productId=059), 
[F455](http://www.homesystems-legrandgroup.com/BtHomeSystems/productDetail.action?productId=051),
[MH200N](http://www.homesystems-legrandgroup.com/BtHomeSystems/productDetail.action?productId=016), 
[F453](http://www.homesystems-legrandgroup.com/BtHomeSystems/productDetail.action?productId=027),  etc.

- **Wireless (ZigBee) USB gateways**, such as [BTicino 3578](http://www.catalogo.bticino.it/BTI-3578-EN) and [Legrand 088328](https://www.legrand.fr/pro/catalogue/35115-interface-openradio/interface-open-webnet-et-radio-pour-pilotage-dune-installation-myhome-play)

## Supported Things

The following Things and OpenWebNet `WHOs` are supported:
### BUS/SCS

| Category   | WHO   | Thing Type IDs                    | Discovery?          | Feedback from BUS?          | Description                                                 | Status           |
| ---------- | :---: | :-------------------------------: | :----------------: | :----------------: | ----------------------------------------------------------- | ---------------- |
| Gateway Management   | `13`  | `bus_gateway`                     | Yes *Testing*                | n/a  | Any IP gateway supporting OpenWebNet protocol should work (e.g. F454 / MyHOMEServer1 / MH202 / F455 / MH200N,...) | Successfully tested: F454, MyHOMEServer1, MyHOME_Screen10, F455, F453AV, MH202, MH200N. Some connection stability issues/gateway resets reported with MH202  |
| Lightning | `1`   | `bus_on_off_switch`, `bus_dimmer` | Yes                | Yes                | BUS switches and dimmers. Green switches.                                                                 | Successfully tested: F411/2, F411/4, F411U2, F422, F429. AM5658 Green Switch. Some discovery issues reported with F429 (DALI Dimmers)  |
| Automation | `2`   | `bus_automation`                | Yes | Yes                  | BUS roller shutters, with position feedback and auto-calibration | Successfully tested: LN4672M2  |
| Temperature Control | `4`   | `bus_thermostat`, `bus_temp_sensor`   | Yes | Yes | Zones room thermostats, external wireless temperature sensors | Successfully tested: HD4692/HD4693 via H3550 Central Unit; H/LN4691; external probes: L/N/NT4577 + 3455 |
| CEN & CEN+ Commands | `15` & `25`   | `bus_cen_scenario_control`, `bus_cenplus_scenario_control`, `bus_dry_contact_ir`   | Yes (CEN/CEN+ by [activation](#discovery-by-activation) only) | Yes | CEN/CEN+ events and virtual activation for scenario control. Dry Contact and IR sensor devices events. | *Testing*: Scenario buttons: HC/HD/HS/L/N/NT4680. Contact interfaces: F428 and 3477. IR sensors: HC/HD/HS/L/N/NT4610 |
| Energy Management | `18`   | `bus_energy_central_unit`   | Yes | Yes | Energy Management Central Unit | Successfully tested: F520, F521 |


### ZigBee (Radio)

| Category   | WHO   | Thing Type IDs                               |    Discovery?  | Feedback from Radio? | Description                                                 | Status                               |
| ---------- | :---: | :------------------------------------------: | :----------------: | :--------: | ----------------------------------------------------------- | ------------------------------------ |
| Gateway    | `13`  | `dongle`                                     |     Yes            | n/a         | Wireless (ZigBee) USB Dongle (BTicino/Legrand models: BTI-3578/088328) | Tested: BTI-3578 and LG 088328             |
| Lightning| `1`   | `dimmer`, `on_off_switch`, `on_off_switch2u` | Yes                | Yes        | ZigBee dimmers, switches and 2-unit switches                | Tested: BTI-4591, BTI-3584, BTI-4585 |
| Automation | `2`   | `automation`                           | Yes | Yes          | ZigBee roller shutters, with position feedback and auto-calibration via a *UP >> DOWN >> Position%* cycle                                                           | *To be tested*    |

## Requirements

This binding requires **openHAB 2.3** or later.

## Installation

The binding it's still *under development* and not part of the official openHAB distribution.
You must **follow carefully the following instruction steps 1a/1b and 2** in order to have the binding properly installed and working.

### 1a. Install from Marketplace

The easiest way to install this binding is from the [Eclipse IoT Marketplace](https://marketplace.eclipse.org/content/openwebnet-2x-binding-testing).

Make sure that the [marketplace plugin is activated](https://www.openhab.org/docs/configuration/eclipseiotmarket.html), and then install the *OpenWebNet binding* from PaperUI (Add-ons -> Bindings -> search for 'openwebnet' then INSTALL).

If you cannot find the binding in the search, probably you have an issue with certificates in your Java environment, that must be updated. Follow [this solution](https://community.openhab.org/t/solved-failed-downloading-marketplace-entries-received-fatal-alert-handshake-failure/52045) to add the required certificates to access all bindings on the Marketplace.

You will have to re-install the binding after an openHAB upgrade. This is a limitation of the Eclipse Marketplace plugin and will be changed in future versions of openHAB.

### 1b. Install Manually

***Alternatively*** this binding can be installed manually:

1. Download the [latest released JAR file](https://github.com/mvalla/openhab2-addons/releases)

1. Copy the JAR file to your openHAB2 `addons` folder. On Linux or RaspberryPi it is under: 

      `/usr/share/openhab2/addons/`

### 2. Activate Dependencies

After the binding is installed, from Marketplace or manually, some *features dependencies must be activated manually*:

- from [Karaf console](https://www.openhab.org/docs/administration/console.html):
    - `feature:install openhab-transport-serial`
    - `feature:install esh-io-transport-upnp`

The binding should now be installed: check in *PaperUI > Configuration > Bindings*.

After upgrading the binding to a new version, there is no need to activate dependencies again.

**However dependencies must be activated again if you upgrade openHAB to a new version or clean its cache.**

## Upgrade of the binding

When upgrading the binding to a new version it's suggested also to remove OpenWebNet Things before uninstalling the old binding, and discover/configure them again after binding has been updated.

### Upgrade - Marketplace installation

1. Goto PaperUI > Add-ons > Bindings > search `openweb` > UNINSTALL
1. reload the page in the browser to make sure latest version is selected
1. search again `openweb` > INSTALL

### Upgrade - Manual installation

Since openHAB uses some cache mechanisms to load bindings, it is not enough to remove and update the binding JAR file from `addons` folder:

1. From [Karaf console](https://www.openhab.org/docs/administration/console.html):
    - `bundle:list` to list all bundles and take note of current bundle `<ID>` for the OpenWebNet Binding
    - `bundle:uninstall <ID>` to remove previous version of the binding
1. remove the previous version of the binding JAR file from `addons/` folder
1. copy the new version of the binding JAR file to `addons/` folder

The new version of the binding should now be installed, check the version number in *PaperUI > Configuration > Bindings*.

## Debugging and Log Files

This binding is currently *under development*. 

To help testing it and provide feedbacks, this is the procedure to set log level to `DEBUG`:

   - from [Karaf console](https://www.openhab.org/docs/administration/console.html):
     - `log:set DEBUG org.openhab.binding.openwebnet`
     - `log:set DEBUG org.openwebnet` 

Both `log:set` commands *are important* to log OWN commands exchanged between the binding and the OpenWebNet gateway.

Log files are written to either `userdata/log` (manual setup) or `/var/log/openhab2` (Linux and RaspberryPi image setup) and can be accessed using standard text reading software. 

The interesting file to provide for feedback is `openhab.log` (`events.log` is not so important).

## Discovery

Gateway and Things discovery is supported using PaperUI by pressing the discovery ("+") button form Inbox.

### BUS/SCS Discovery

- BUS Gateway automatic discovery will work only for newer gateways supporting UPnP: F454, MyHOMEServer1, MH202, MH200N, MyHOME_Screen 10.
For other gateways you can add them manually, see [Thing Configuration](#thing-configuration) below.
- After gateway is discovered and added a connection with default password (`12345`) is tested first: if it does not work the gateway will go offline and an error status will be set. A correct password must then be set in the gateway Thing configuration otherwise the gateway will not become online.
- Once the gateway is online, a second Scan request from Inbox will discover BUS devices
- BUS/SCS Dimmers must be ON and dimmed (30%-100%) during a Scan, otherwise they will be discovered as simple On/Off switches
    - *KNOWN ISSUE*: In some cases dimmers connected to a F429 Dali-interface are not automatically discovered
- CEN/CEN+ Scenario Control devices will be discovered by activation only. See [discovery by activation](#discovery-by-activation) for details.
After confirming a discovered CEN/CEN+ device from Inbox, activate again its scenario buttons and refresh the PaperUI Control page to see button channels appear.

#### Discovery by Activation
Devices can also be discovered if activated while a Inbox Scan is active: start a new Scan, wait 15-20 seconds and then while the _Scan is still active_ (spinning arrow in Inbox), activate the physical device (for example dim the dimmer, push a CEN/CEN+ Scenario button, etc.) to have it discovered by the binding.

If a device cannot be discovered automatically it's always possible to add them manually, see [Configuring Devices](#configuring-devices).

### Wireless (ZigBee) Discovery

- The USB dongle must be inserted in one of the USB ports of the openHAB computer before discovery is started
- ***IMPORTANT NOTE:*** As for the OH serial binding, on Linux the `openhab` user must be member of the `dialout` group, to be able to use USB/serial port:

    ```
    $ sudo usermod -a -G dialout openhab
    ```

    + The user will need to logout and login to see the new group added. If you added your user to this group and still cannot get permission, reboot Linux to ensure the new group permission is attached to the `openhab` user.
- Once the gateway is discovered and added, a second discovery request from Inbox will discover devices. Because of the ZigBee radio network, discovery will take ~40-60 sec. Be patient!
- Wireless devices must be part of the same ZigBee network of the USB dongle to discover them. Please refer to [this guide by BTicino](http://www.bticino.com/products-catalogue/management-of-connected-lights-and-shutters/#installation) to setup a ZigBee wirelss network which includes the USB dongle
- Only powered wireless devices part of the same ZigBee network and within radio coverage of the USB dongle will be discovered. Unreachable or not powered devices will be discovered as *GENERIC* devices and cannot be controlled. Control units cannot be discovered by the USB dongle and therefore are not supported


## Thing Configuration

### Configuring BUS/SCS Gateway

To add a gateway manually using PaperUI: go to *Inbox > "+" > OpenWebNet > click `ADD MANUALLY`* and then select `OpenWebNet BUS Gateway` device.

Parameters for configuration:

- `host` : IP address / hostname of the BUS/SCS gateway (*mandatory*)
   - Example: `192.168.1.35`
- `port` : port (*optional*, default: `20000`)
- `passwd` : gateway password (*required* for gateways that have a password set. Default: `12345`)
   - Example: `abcde` or `12345`
   - if the BUS/SCS gateway is configured to accept connections from the openHAB computer IP address, no password should be required
   - in all other cases, a password must be set. This includes  gateways that have been discovered and added from Inbox that without a password settings will not become ONLINE
- `discoveryByActivation` : **=EXPERIMENTAL=** discover BUS devices when they are activated also when a device scan is not currently active (*optional*, default: `false`)

Alternatively the BUS/SCS Gateway thing can be configured using the `.things` file, see `openwebnet.things` example [below](#full-example).

### Configuring Wireless (ZigBee) USB Dongle

The wirelss ZigBee USB dongle is discovered automatically and added in Inbox. Manual configuration is not supported at the moment.

### Configuring Devices

Devices can be discovered automatically from Inbox after the gateway has been configured and connected.

Devices can be also added manually from PaperUI. For each device it must be configured:

- the associated gateway (`Bridge Selection` menu)
- the `WHERE` config parameter (`OpenWebNet Device Address`):
  - example for BUS/SCS: Point to Point `A=2 PL=4` --> `WHERE="24"`
  - example for BUS/SCS: Point to Point `A=6 PL=4` on local bus --> `WHERE="64#4#01"`
  - example for BUS/SCS thermo Zones: `Zone=1` --> `WHERE="1"`; external probe `5` --> `WHERE="500"`
  - example for ZigBee/wireless: use decimal format address without the UNIT part and network: ZigBee `WHERE=414122201#9` --> `WHERE="4141222"`
  - for CEN+ use 2+N[0-2047]; example Scenario Control 5 --> WHERE=25


## Channels

Devices support some of the following channels:

| Channel Type ID (channel ID)        | Item Type     | Description                                                             | Read/Write |
|--------------------------|---------------|-------------------------------------------------------------------------|:----------:|
| `switch`                 | Switch        | To switch the device `ON` and `OFF`                                     |    R/W     |
| `brightness`             | Dimmer        | To adjust the brightness value (Percent, `ON`, `OFF`)                   |    R/W     |
| `shutterPosition`        | Rollershutter | To activate roller shutters (`UP`, `DOWN`, `STOP`, Percent - [see Shutter position](#shutter-position)) |    R/W     |
| `shutterMotion`          | Number        | The motion currently roller shutters (`0`= stop, `1`= up, `2`= down)     |     R      |
| `temperature`            | Number        | The zone currently sensed temperature (°C)                              |     R      |
| `targetTemperature`      | Number        | The zone target temperature (°C). It considers `setPoint` but also `activeMode` and `localMode`  |      R     |
| `thermoFunction`         | String        | The zone set thermo function: `HEAT`, `COOL` or `GENERIC` (heating + cooling)     |      R     |
| `heatingCoolingMode` [*] | String        | The zone mode: `heat`, `cool`, `heatcool`, `off` (same as `thermoFunction`+ `off`, useful for Google Home integration)    |     R      |
| `heating`  [*]           | Switch        | `ON` if the zone heating actuator is currently active (heating is On) [see heating and cooling](#heating-and-cooling)     |     R      |
| `cooling`  [*]           | Switch        | `ON` if the zone cooling actuator is currently active (cooling is On) [see heating and cooling](#heating-and-cooling)     |     R      |
| `activeMode`             | String        | The zone current active mode (Operation Mode): `AUTO`, `MANUAL`, `PROTECTION`, `OFF`. It considers `setMode` and `localMode` (with priority)     |      R     |
| `localMode`              | String        | The zone current local mode, as set on the physical thermostat in the room: `-3/-2/-1/NORMAL/+1/+2/+3`, `PROTECTION`, or `OFF`  |      R     |
| `setpointTemperature`    | Number        | The zone setpoint temperature (°C), as set from Central Unit or openHAB |     R/W    |
| `setMode`                | String        | The zone set mode, as set from Central Unit or openHAB: `AUTO`, `MANUAL`, `PROTECTION`, `OFF`    |     R/W    |
| `scenarioButton` (`button_X`)         | String        | Events or virtual pressure for CEN/CEN+ scenario buttons: `PRESSED`, `RELEASED`, `PRESSED_EXT`, `RELEASED_EXT` [see possible values](#scenariobutton)  |     R/W      |
| `dryContactIR`  (`sensor`)        | Switch        | Indicates if a Dry Contact interface is `ON`/`OFF`, or if a IR Sensor is detecting movement (`ON`), or not  (`OFF`) |     R      |
| `power`                  | Number        | The actual active power usage from Energy Management Central Unit       |     R      |

[*] = advanced channel: in PaperUI can be shown from  *Thing config > Channel list > Show More* button. Link to an item by clicking on the channel blue button.

### Notes on channels

#### `shutterPosition` position

For Percent commands and position feedback to work correctly, the `shutterRun` Thing config parameter must be configured equal to the time (in ms) to go from full UP to full DOWN.
It's possible to enter a value manually or set `shutterRun=AUTO` (default) to calibrate shutterRun parameter automatically the first time a Percent command is sent to the shutter: a *UP >> DOWN >> Position%* cycle will be performed automatically.

- if shutterRun is not set, or is set to AUTO but calibration has not been performed yet, then position estimation will remain `UNDEFINED`
- if shutterRun is set manually and too higher than the actual runtime, then position estimation will remain `UNDEFINED`: try to reduce shutterRun until you find the right value
- before adding/configuring roller shutter Things (or installing a binding update) it is suggested to have all roller shutters `UP`, otherwise the Percent command won’t work until the roller shutter is fully rolled up
- if the gateways gets disconnected then the binding cannot know anymore where the shutter was: if `shutterRun` is defined (and correct), then just roll the shutter all Up / Down and its position will be estimated again
- the shutter position is estimated based on UP/DOWN timing and therefore an error of ±2% is normal

#### `heating` and `cooling`

`heating` and `cooling` channels assume that your BTicino heating/cooling actuators are configured as #1 and #2 respectively.
To ensure the heating/cooling actuators are set up correctly for a Thermostat:
1. open *MyHome_Suite*
2. go to Thermostat configuration panel > *Plant settings* 
3. in *Actuators section* check if the first actuator listed is numbered "1". This will be the actuator which state will be returned by the binding in the `heating` channel.
4. if it is not numbered "1", find the actuator device corresponding to the same Thermo zone of the Thermostat and set the "Device number" property to "1"
5. repeat steps 3. and 4. for `cooling` using actuator number "2"

#### `scenarioButton`

- In CEN/CEN+ channels are named `button_X` where `X` is the button number on the Scenario Control device
- Button channels appear in PaperUI after the first time the corresponding button is activated from the physical device. Refresh the PaperUI/Control page if needed
- When using file configuration, in the Thing configuration use the `buttons` parameter to define a comma-separated list of buttons numbers [0-31] configured for the scenario device, example: `buttons=1,2,4`. See [openwebnet.things](#openwebnet-things) for an example
- channel possible values are:
    - `PRESSED` and then just after `RELEASED` when a CEN/CEN+ button is short-pressed (<0.5sec)
    - `PRESSED_EXT` (updated again every 0.5sec) and then `RELEASED_EXT` when a CEN/CEN+ button is long pressed (>=0.5sec)
- Sending on channels `button_X` the commands: `PRESSED`, `RELEASED`, etc. will simulate a *virtual short/long pressure* of the corresponding CEN/CEN+ button, enabling the activation of MH202 scenarios on the BUS from openHAB. See [openwebnet.sitemap](#openwebnet-sitemap) & [openwebnet.rules](#openwebnet-rules) sections for an example


## Integration with assistants

To be visible to assistants like Google Assistant/Amazon Alexa/Apple HomeKit (Siri) an item must have the correct tag.
Items created automatically with PaperUI (Simple Mode item linking: `Configuration > System > Item Linking > Simple mode > SAVE`) will get automatically the correct tag from the binding: in particular items associated with these channels will have the following tags:

- `switch` / `brightness` channels will have the `Lighting` tag
- `shutterPosition` channel will have the `Blinds` and `Switchable` tag
- `temperature` channel will have the `CurrentTemperature` tag
- `setpointTemperature` channel will have the `TargetTemperature` tag
- `heatingCoolingMode` channel will have the `homekit:HeatingCoolingMode` tag

After configuration, you can double-check which tags are set looking at the `tags` attribute in the REST API: http://openhabianpi.local:8080/rest/items.

**NOTE For items created automatically with PaperUI tags are added automatically by the OpenWebNet binding, but you have to check which tags are actually supported by each openHAB add-on (Google Assistant/Alexa/HomeKit). 
For example the Google Assitant add-on for openHAB does not support the 'Blinds' tag yet.**

After items and their tags are set, it will be enough to link openHAB with [myopenhab](https://www.openhab.org/addons/integrations/openhabcloud/) and with the Google Assistant/Alexa/HomeKit add-on, and you will be able to discover/control BTicino items.

Names used will be the names of the channels (Brightness, etc.); they cannot be changed in PaperUI, usually you can change names in the assistants.

Note that the most flexible configuration is obtained using `.items` file: see the examples below.

See these official docs and other threads in the OH community for more information about Google Assistant/Alexa/HomeKit integration and configuration:

- Google Assistant (Google Home): <https://www.openhab.org/docs/ecosystem/google-assistant/>
- Amazon Alexa: <https://www.openhab.org/docs/ecosystem/alexa/>
- Apple HomeKit (Siri): <https://www.openhab.org/addons/integrations/homekit/>

***NOTE***
You will need to add tags manually for items created using PaperUI when Simple Mode item linking is de-activated, or for items created using `.items` file.

## Full Example

### openwebnet.things:

```xtend
Bridge openwebnet:bus_gateway:mybridge "MyHOMEServer1" [ host="192.168.1.35", passwd="abcde" ] {
      bus_on_off_switch        LR_switch        "Living Room Light"       [ where="51" ]
      bus_dimmer               LR_dimmer        "Living Room Dimmer"      [ where="25#4#01" ]
      bus_dimmer               LR_dalidimmer    "Living Room Dali-Dimmer" [ where="0311#4#01" ]
      bus_automation           LR_shutter       "Living Room Shutter"     [ where="93", shutterRun="10050"]
      bus_thermostat           LR_thermostat    "Living Room Thermostat"  [ where="1"]
      bus_temp_sensor          EXT_tempsensor   "External Temperature"    [ where="500"]
      bus_energy_central_unit  CENTRAL_energy   "Energy Management"       [ where="51" ]
      bus_cen_scenario_control LR_CEN_scenario  "Living Room CEN" [ where="51", buttons="4,3,8"]
      bus_cenplus_scenario_control  LR_CENplus_scenario "Living Room CEN+"        [ where="212", buttons="1,5,18" ]
      bus_dry_contact_ir       LR_IR_sensor     "Living Room IR Sensor"   [ where="399" ]
}
``` 


```xtend
<TODO----- ZigBee Dongle configuration only needed for radio devices >
Bridge openwebnet:dongle:mydongle2  [serialPort="kkkkkkk"] {
    dimmer          myzigbeedimmer [ where="xxxxx"]
    on_off_switch   myzigbeeswitch [ where="yyyyy"]
}
```

### openwebnet.items:

Items in the example (Light, Dimmer, Thermostat, etc.) will be discovered by Google Assistant/Alexa/HomeKit if their tags are configured like in the example.

```xtend
Switch         iLR_switch        "Switch"                 <light>          (gLivingRoom)                [ "Lighting" ]  { channel="openwebnet:bus_on_off_switch:mybridge:LR_switch:switch" }
Dimmer         iLR_dimmer        "Brightness [%.0f %%]"   <DimmableLight>  (gLivingRoom)                [ "Lighting" ]  { channel="openwebnet:bus_dimmer:mybridge:LR_dimmer:brightness" }
Dimmer         iLR_dalidimmer    "Brightness [%.0f %%]"   <DimmableLight>  (gLivingRoom)                [ "Lighting" ]  { channel="openwebnet:bus_dimmer:mybridge:LR_dalidimmer:brightness" }
/* For Dimmers, use category DimmableLight to have Off/On switch in addition to the Percent slider in PaperUI */
Rollershutter  iLR_shutter       "Shutter [%.0f %%]"      <rollershutter>  (gShutters, gLivingRoom)     [ "Blinds"   ]  { channel="openwebnet:bus_automation:mybridge:LR_shutter:shutterPosition" }
Number         iLR_shuttermotion "Shutter motion"                          (gShutters, gLivingRoom)                     { channel="openwebnet:bus_automation:mybridge:LR_shutter:shutterMotion" }
Number         iEXT_tempsensor   "Temperature [%.1f °C]"  <temperature>                                 [ "CurrentTemperature" ]  { channel="openwebnet:bus_temp_sensor:mybridge:EXT_tempsensor:temperature" }
Number         iCENTRAL_en_power "Power [%.0f W]"         <energy>            { channel="openwebnet:bus_energy_central_unit:mybridge:CENTRAL_energy:power" }
String         iLR_scenario_btn4  "Scenario Button 4"     <network>           { channel="openwebnet:bus_cen_scenario_control:mybridge:LR_CEN_scenario:button_4" }  
String         iLR_scenario_btn1  "Scenario Button 1"     <network>           { channel="openwebnet:bus_cenplus_scenario_control:mybridge:LR_CENplus_scenario:button_1" }  
Switch         iLR_IR_sensor      "Living Room IR sensor" <motion>            { channel="openwebnet:bus_dry_contact_ir:mybridge:LR_IR_sensor:sensor" }

/* Thermostat Setup (Google Assitant/Alexa require thermostat items to be grouped together) */
Group   gLR_thermostat               "Living Room Thermostat"                                     [ "Thermostat" ]
Number:Temperature  iLR_temp         "Temperature [%.1f °C]"  <temperature>  (gLR_Thermostat)     [ "CurrentTemperature" ]          { channel="openwebnet:bus_thermostat:mybridge:LR_thermostat:temperature" }
String              iLR_offset       "Offset"                                (gLR_Thermostat)                                       { channel="openwebnet:bus_thermostat:mybridge:LR_thermostat:localMode" }
Switch              iLR_heating      "Heating is"                            (gLR_Thermostat)                                       { channel="openwebnet:bus_thermostat:mybridge:LR_thermostat:heating" }
Switch              iLR_cooling      "Cooling is"                            (gLR_Thermostat)                                       { channel="openwebnet:bus_thermostat:mybridge:LR_thermostat:cooling" }
Number:Temperature  iLR_targetTemp   "Target [%.1f °C]"                      (gLR_Thermostat)                                       { channel="openwebnet:bus_thermostat:mybridge:LR_thermostat:targetTemperature" }
String              iLR_activeMode   "Active Mode"                           (gLR_Thermostat)                                       { channel="openwebnet:bus_thermostat:mybridge:LR_thermostat:activeMode" }
String              iLR_heatCool     "HeatingCoolingMode"                    (gLR_Thermostat)     [ "homekit:HeatingCoolingMode" ]  { channel="openwebnet:bus_thermostat:mybridge:LR_thermostat:heatingCoolingMode" }
Number:Temperature  iLR_setpointTemp "Setpoint Temperature [%.1f °C]"  <temperature> (gLR_Thermostat)  [ "TargetTemperature" ]      { channel="openwebnet:bus_thermostat:mybridge:LR_thermostat:setpointTemperature" }
String              iLR_setMode      "Set Mode"                              (gLR_Thermostat)                                       { channel="openwebnet:bus_thermostat:mybridge:LR_thermostat:setMode"}
```

### openwebnet.sitemap

```xtend
sitemap openwebnet label="OpenWebNet Binding Example Sitemap"
{

Frame label="Living Room"
    {
          Default item=iLR_switch           icon="light"    
          Default item=iLR_dimmer           icon="light" 
          Default item=iLR_dalidimmer       icon="light"
          Default item=iLR_shutter
          Default item=iEXT_tempsensor      icon="temperature"
          Switch    item=iLR_scenario_btn4 label="CEN Scenario (btn4)[]"  mappings=[PRESSED="Scenario-A (PRESSED)", PRESSED_EXT="Scenario-B (PRESSED_EXT)", RELEASED_EXT="Scenario-B-end (RELEASED_EXT)"] 
          Switch    item=iLR_scenario_btn1 label="CEN+ Scenario (btn1)[]" mappings=[PRESSED="Scenario-C (PRESSED)", PRESSED_EXT="Scenario-C (PRESSED_EXT)", RELEASED_EXT="Scenario-C-end (RELEASED_EXT)"] 

          Switch item=iLR_IR_sensor mappings=[ON="Presence", OFF="No Presence"]

          Group item=gLR_thermostat label="Thermostat" icon="heating"
          { 
             Default    item=iLR_temp  label="Current Temperature"  icon="temperature"
             Default    item=iLR_targetTemp label="Target Temperature"  icon="temperature"
             Default    item=iLR_offset  icon="heating"   visibility=[iLR_offset != "NORMAL"]
             Text       item=iLR_heating icon="fire"  label="Heating is active" labelcolor=["red"]  visibility=[iLR_heating=="ON"]
             Text       item=iLR_cooling icon="climate"  label="Cooling is active" labelcolor=["blue"] visibility=[iLR_cooling=="ON"]
             Selection  item=iLR_activeMode icon="radiator" mappings=[AUTO="Auto", MANUAL="Manuale", PROTECTION="Anti-gelo", OFF="Spento"] valuecolor=[AUTO="green", MANUAL="orange", PROTECTION="blue", OFF="red"] 
             Selection  item=iLR_setMode icon="heating"   mappings=[AUTO="Auto", MANUAL="Manuale", PROTECTION="Anti-gelo", OFF="Spento"] 
             Setpoint   item=iLR_setpointTemp label="Set Temp [%.1f °C]" minValue=12.5 maxValue=30 step=0.5
          }
    }

}
```

### openwebnet.rules

```xtend
// SCENARIO-A: short pressure on CEN+ button 1 will increase dimmer%
rule "CEN+ dimmer increase"
when
    Item iLR_scenario_btn1 received update "RELEASED"
then
        sendCommand(iLR_dimmer, INCREASE)  
end

// SCENARIO-B: long pressure on CEN+ button 1 will switch off dimmer
rule "CEN+ dimmer off"
when
    Item iLR_scenario_btn1 received update "RELEASED_EXT"
then
        sendCommand(iLR_dimmer, OFF)  
end
```

## FAQs & Known Issues

### FAQs

#### I want to control blinds from Google Home (Google Assistant): how?
Blinds are not currently supported by the Google Home add-on for openHAB, this is not a limitation of this binding. 
See: https://github.com/openhab/openhab-google-assistant/issues/59

#### My BTicino devices are visible from PaperUI but cannot be discovered by Google Home / Alexa
Not all device types are supported by Google Home / Alexa and the respective openHAB add-ons. This is not a limitation of the binding.
Visit the links at the end of section [Integration with Assistants](#integration-with-assistants) to check compatibility with your assistant.

#### When message/feature XXXX will be supported ?
You can check if someone has already requested support for a message/feature here: [GitHub repo](https://github.com/mvalla/openhab2-addons/issues).
If not, add a new issue. Issues are organised by milestones, but deadlines of course are not guaranteed (other volunteer developers are welcome!).

### Known Issues
For a full list of current open issues / features requests see [GitHub repo](https://github.com/mvalla/openhab2-addons/issues)

- With some latest firmware versions of MyHOMEServer_1, rollershutters are not discovered because this gateways responds to device status request with a invalid OpenWebNet message. This is a BUG by BTicino and not a problem of the binding.
See: https://github.com/mvalla/openhab2-addons/issues/34

- In some cases dimmers connected to a F429 Dali-interface cannot be discovered, even if switched ON and dimmed. This looks like as a limitation of some gateways that do not report status of Dali devices when requested.
See: https://github.com/mvalla/openhab2-addons/issues/14

## Changelog

**v2.5.0.M2-1** - 20/03/2019
- [FIX #66] USB dongle (gateway) cannot connect anymore
- [FIX #65] Rollershutter % Position does not work in 2.5.0.M2
- removed Switchable tag to shutter channel


**v2.5.0.M2** - 08/03/2019
- [FIX #29] Fixed (again) Automation command translation (1000#)
- [FIX] Fixed Energy Meter subscription (every 10min)
- [FIX] corrected deviceWhere address management for ZigBee devices and discovery of ON_OFF_SWITCH_2UNITS
- [FIX #54] CEN scenarios are now detected when activated from Touchscreens (added PRESSURE>RELEASE schedule)
- [FIX #56] Thermostat Cannot set setpoint temperature (now WHERE=N is first used, then WHERE=#N if it fails)
- [FIX #59] added discoverByActivation parameter (optional) to BUS gateway
- added FAQs section to README.md
- added ownId as representation-property
- added Switchable tag to shutter channel

**v2.5.0.M1** - 28/01/2019

- **[FIX #28] automatic discovery of BUS gateways is now supported**
- gateway model, firmwareVersion and serialNumber are now read from UPnP discovery
- [FIX #39] set subscription interval for Energy Meter
- [FIX #4] added support for BTicino movement sensors (like AM5658 Green Switch) 
- updated to openHAB 2.5.0 dev branch

**v2.4.0-b9-2** - 18/01/2019

- [FIX #37] CEN commands WHAT (buttons) 0-9 are now 00-09
- [FIX] CEN/CEN+ scenarioButton channel is now able to receive commands
- [FIX #45] Thermoregulation setpoint command refused
- [FIX #46] Thermo: activeMode displays only the state “Off" and "anti freeze”
- [FIX #42 & #43] Devices with same WHERE receive wrong messages from BUS
- [FIX] improved device registration to BridgeHandler

**v2.4.0-b9-1** - 27/12/2018

- [FIX #6] and [FIX #33] Initial support for `WHO=15/25` CEN/CEN+ for receiving events from BUS Scenario Control physical devices/buttons (for example Scenario Control: HC/HD/HS/L/N/NT4680) and sending virtual pressure commands to activate MH202 scenarios on the BUS from openHAB. Use [discovery by activation](#discovery-by-activation) to discover CEN/CEN+ scenario control devices. Further buttons/channels are discovered by pressing the corresponding physical button after the device has been added from Inbox
- [FIX #9] Support for `WHO=25` Dry Contact interfaces and IR Sensors on BUS, with discovery
- [FIX #11] Initial support for `WHO=18` Energy Management on BUS, with discovery. Currently supported: Energy Management Central Unit (F521) power measures
- [FIX #29] Added support for command translation (1000# ) for Automation
- [FIX #27] Device **Discovery by Activation** ( [discovery by activation](#discovery-by-activation) ) for Lighting and CEN/CEN+: if a BUS physical device is not found in Inbox during a Scan, activate the device to discover it

**v2.4.0-b8** - 11/11/2018

- [FIX #25] added `Blinds` tag for shutter channels (Rollershutter items) 
- [FIX #17] now a disconnection from the gateway is detected within few minutes
- [FIX #10] now an automatic STOP command is sent when a new Position/UP/DOWN command is sent while already moving
- [FIX #18] at startup (for example after a power outage) the binding now tries periodically to connect to the BTicino Gateway
- [BUG] now a decimal setpointTemperature (21.5 °C) is sent as decimal to the thermostat and not as integer
- [FIX #20] added support for dimmerLevel100 levels when dimmers are changed from myhomescreen or touchscreen 
- [FIX #16] dimmers now save last state if switched off from openHAB
- [FIX] now Command Translation (1000#WHAT) is supported in Lighting
- [FIX] setpointTemperature now uses QuantityType (Number:Temperature item)
- updated README examples and added .sitemap example
- moved to new repository, forked from openhab/openhab2-addons

**v2.4.0-b7** - 01/09/2018

- [FIX #5] Initial support for `WHO=4` Thermoregulation on BUS. Currently supported: zones room thermostats and external (wireless) temperature sensors. Both heating and cooling functions are supported
- The binding is now available on the [Eclipse IoT Marketplace](https://marketplace.eclipse.org/content/openwebnet-2x-binding-testing)
- [BUG] corrected a bad bug on the connection part (originally taken from the BTicino 1.x binding) that caused loosing many monitoring (feedback) messages from the gateway when several messages were received together
- [FIX #13] now commands sent close in a short time are sent re-using the same socket connection to the gateway. This should improve connection stability and speed in general and in particular with older BUS gateways (e.g. MH202)
- [FIX] improved Shutter management and position estimation, thanks to previous 2 enhancements
- minimum requirement is now openHAB 2.3, which is needed to support measurements units like °C

**v2.4.0-b6** - 02/07/2018

- updated to openHAB 2.4.0 dev branch
- [FIX #7] added support for inverted UP/DOWN automation commands for older USB ZigBee dongles
- [BUG] some switches were wrongly discovered as dimmers (now use only commands for device discovery)
- [FIX] added support for SCS/ZIGBEE_SHUTTER_SWITCH (515/513) device types
- [FIX] added support for F455 gateways using `*99*0##` command session

**v2.3.0-b5** - 26/05/2018

- [FIX #1] state monitoring from BUS (feedback) is no longer stopped if unsupported messages are received from BUS
- [FIX] automatic reconnect to BUS when connection is lost
- [NEW] support for Gateways with string passwords (HMAC authentication), like MyHOMEServer1 
- [NEW] support for `WHO=2` Automation (shutters), both on BUS and ZigBee, with position feedback and  goto Percent. It requires setting the shutter run-time in the thing configuration. Experimental auto-calibration of the run-time is also supported!

**v2.3.0-b4** - 09/04/2018

- first public release

## Disclaimer

- This binding is not associated by any means with BTicino or Legrand companies
- Contributors of this binding have no liability for any direct, indirect, incidental, special, exemplary, or consequential damage to things or people caused by using the binding connected to a real BTicino/Legrand (OpenWebNet) plant/system and its physical devices. The final user is the only responsible for using this binding in a real environment. See Articles 5. and 6. of [Eclipse Public Licence 1.0](https://www.eclipse.org/legal/epl-v10.html) under which this binding software is distributed
- The OpenWebNet protocol is maintained and Copyright by BTicino/Legrand. The documentation of the protocol if freely accessible for developers on the [MyOpen Community website - https://www.myopen-legrandgroup.com/developers](https://www.myopen-legrandgroup.com/developers/)
- OpenWebNet, MyHOME and MyHOME_Play are registered trademarks by BTicino/Legrand
- This binding uses `openwebnet-lib 0.9.x`, an OpenWebNet Java lib partly based on [openwebnet/rx-openwebnet](https://github.com/openwebnet/rx-openwebnet) client library by @niqdev, to support:
    - gateways and OWN frames for ZigBee
    - frame parsing
    - monitoring events from BUS
  The lib also uses few modified classes from the openHAB 1.x BTicino binding for socket handling and priority queues.

## Special thanks

Special thanks for helping on testing this binding go to:
[@m4rk](https://community.openhab.org/u/m4rk/),
[@bastler](https://community.openhab.org/u/bastler),
[@gozilla01](https://community.openhab.org/u/gozilla01),
[@enrico.mcc](https://community.openhab.org/u/enrico.mcc),
[@k0nti](https://community.openhab.org/u/k0nti/),
[@gilberto.cocchi](https://community.openhab.org/u/gilberto.cocchi/),
[@llegovich](https://community.openhab.org/u/llegovich),
[@gabriele.daltoe](https://community.openhab.org/u/gabriele.daltoe)
and many others at the fantastic openHAB community!

