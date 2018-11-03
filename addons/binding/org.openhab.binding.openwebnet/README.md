# OpenWebNet (BTicino/Legrand) Binding

This new binding integrates BTicino / Legrand MyHOME(r) BUS & ZigBee wireless (MyHOME_Play) devices using the **[OpenWebNet](https://en.wikipedia.org/wiki/OpenWebNet) protocol**.
It is the first known binding for openHAB 2 that **supports *both* wired BUS/SCS** as well as **wireless setups**, all in the same biding. The two networks can be configured simultaneously.
It's also the first OpenWebNet binding with initial support for discovery of BUS/SCS devices.
Commands from openHAB and feedback (events) from BUS/SCS and wireless network are supported.

Support for both numeric (`12345`) and alpha-numeric (`abcde` - HMAC authentication) gateway passwords is included.

## Prerequisites

In order for this biding to work, an OpenWebNet gateway is needed in your home system to talk to devices.
Currently these gateways are supported by the binding:

- **IP gateways** or scenario programmers, such as BTicino 
[F454](http://www.homesystems-legrandgroup.com/BtHomeSystems/productDetail.action?productId=006), [MyHOMEServer1](http://www.bticino.com/products-catalogue/myhome_up-simple-home-automation-system/), 
[MyHOME_Screen 10](http://www.homesystems-legrandgroup.com/BtHomeSystems/productDetail.action?lang=EN&productId=001), 
[MH202](http://www.homesystems-legrandgroup.com/BtHomeSystems/productDetail.action?productId=059), [F455](http://www.homesystems-legrandgroup.com/BtHomeSystems/productDetail.action?productId=051),
[MH200N](http://www.homesystems-legrandgroup.com/BtHomeSystems/productDetail.action?productId=016), 
[F453](http://www.homesystems-legrandgroup.com/BtHomeSystems/productDetail.action?productId=027),  etc.
- **Wireless (ZigBee) USB gateways**, such as [BTicino 3578](http://www.catalogo.bticino.it/BTI-3578-EN) and [Legrand 088328](https://www.legrand.fr/pro/catalogue/35115-interface-openradio/interface-open-webnet-et-radio-pour-pilotage-dune-installation-myhome-play)

## Supported Things

The following Things and OpenWebNet `WHOs` are supported:
### BUS/SCS

| Category   | WHO   | Thing Type IDs                    | Discovery?          | Feedback from BUS?          | Description                                                 | Status           |
| ---------- | :---: | :-------------------------------: | :----------------: | :----------------: | ----------------------------------------------------------- | ---------------- |
| Gateway    | `13`  | `bus_gateway`                     | *work in progress*                | n/a  | Any IP gateway supporting OpenWebNet protocol should work (e.g. F454 / MyHOMEServer1 / MH202 / F455 / MH200N,...) | Successfully tested: F454, MyHOMEServer1, MyHOME_Screen10, F455, F453AV, MH202. Some connection stability issues/gateway resets reported with MH202  |
| Lightning | `1`   | `bus_on_off_switch`, `bus_dimmer` | Yes                | Yes                | BUS switches and dimmers                                                                 | Successfully tested: F411/2, F411/4, F411U2, F422, F429. Some discovery issues reported with F429 (DALI Dimmers)  |
| Automation | `2`   | `bus_automation`                | Yes | Yes                  | BUS roller shutters, with position feedback and auto-calibration via a *UP >> DOWN >> Position%* cycle                                                                                       | Successfully tested: LN4672M2  |
| Temperature Control | `4`   | `bus_thermostat`, `bus_temp_sensor`   | Yes | Yes | Zones room thermostats, external wireless temperature sensors | Successfully tested: HD4692/HD4693 via H3550 Central Unit; H/LN4691 via BTI-3488 Central Unit; external probes: L/N/NT4577 + 3455 |

### ZigBee (Radio)

| Category   | WHO   | Thing Type IDs                               |    Discovery?  | Feedback from Radio? | Description                                                 | Status                               |
| ---------- | :---: | :------------------------------------------: | :----------------: | :--------: | ----------------------------------------------------------- | ------------------------------------ |
| Gateway    | `13`  | `dongle`                                     |     Yes            | n/a         | Wireless (ZigBee) USB Dongle (BTicino/Legrand models: BTI-3578/088328) | Tested: BTI-3578 and LG 088328             |
| Lightning| `1`   | `dimmer`, `on_off_switch`, `on_off_switch2u` | Yes                | Yes        | ZigBee dimmers, switches and 2-unit switches                | Tested: BTI-4591, BTI-3584, BTI-4585 |
| Automation | `2`   | `automation`                           | Yes | Yes          | ZigBee roller shutters, with position feedback and auto-calibration via a *UP >> DOWN >> Position%* cycle                                                           | *To be tested*    |

## Installation

The binding it's still *under development* and not part of the official openHAB distribution.

### Requirements

This binding requires **openHAB 2.3** or later.

### Install from Marketplace

The easiest way to install this binding is from the [Eclipse IoT Marketplace](https://marketplace.eclipse.org/content/openwebnet-2x-binding-testing).

Make sure that the [marketplace plugin is activated](https://www.openhab.org/docs/configuration/eclipseiotmarket.html), and then install the *OpenWebNet binding* from PaperUI (Add-ons -> Bindings -> search for 'openwebnet' then INSTALL).

Be aware that you might have to re-install the binding after an openHAB upgrade. This is a limitation of the Eclipse Marketplace plugin and will be changed in future versions of openHAB.

### Install Manually

Alternatively this binding can be installed manually:

1. Download the [latest released JAR file](https://github.com/mvalla/openhab-openwebnet/releases)

1. Copy the JAR file to your openHAB2 `addons` folder. On Linux or RaspberryPi is under: 

      `/usr/share/openhab2/addons/`

### Activate Dependencies

After the binding is installed, from Marketplace or manually, some features dependencies must be activated manually:

- from [Karaf console](https://www.openhab.org/docs/administration/console.html):
    - `feature:install openhab-transport-serial`
    - if you are using  **openHAB 2.4.0-xxx** also type:
	
 `feature:install esh-io-transport-upnp`

The binding should now be installed: check in *PaperUI > Configuration > Bindings*.

### Upgrade from previous binding release version

Since openHAB uses some cache mechanisms to load bindings, it is not enough to remove and update the binding JAR file.

It's suggested also to remove OpenWebNet Things before uninstalling the old binding, and discover/configure them again after binding has been updated.

1. From [Karaf console](https://www.openhab.org/docs/administration/console.html):
    - `bundle:list` to list all bundles and take note of current bundle `<ID>` for the OpenWebNet Binding
    - `bundle:uninstall <ID>` to remove previous version of the binding
1. remove the previous version of the binding JAR file from `addons/` folder
1. copy the new version of the binding JAR file to `addons/` folder
 
While upgrading, there is no need to activate dependencies again.

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

Things discovery is supported using PaperUI by activating the discovery ("+") button form Inbox.

### BUS/SCS Discovery

- Gateway discovery using UPnP is *under development* and will be available only for IP gateways supporting UPnP
- For the moment the OpenWebNet IP gateway should be added manually from PaperUI or in the .things file (see [Configuring BUS/SCS Gateway](#configuring-bus-scs-gateway) below)
- IP and passoword (if needed) must be configured
- Once the gateway is added manually as a Thing, a second discovery request from Inbox will discover BUS devices
- BUS/SCS Dimmers must be ON and dimmed (30-100%) at time of discovery, otherwise they will be discovered as simple On/Off switches
    - *KNOWN ISSUE*: In some cases dimmers connected to F429 Dali-interface are not discovered with the current method. If they are not discovered automatically it's always possible to add them manually, see [Configuring Devices](#configuring-devices).

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

To configure the gateway using PaperUI: go to *Inbox > "+" > OpenWebNet > click `ADD MANUALLY`* and then select `OpenWebNet BUS Gateway` device, with this configuration:

- `host` : IP address / hostname of the BUS/SCS gateway (*mandatory*)
   - Example: `192.168.1.35`
- `port` : port (*optional*, default: `20000`)
- `passwd` : gateway password (*optional*)
   - Example: `abcde`
   - if the BUS/SCS gateway is configured to accept connections from the openHAB computer IP address, no password should be required

Alternatively the BUS/SCS Gateway thing can be configured using the `.things` file:

```
Bridge openwebnet:bus_gateway:mybridge [ host="192.168.1.35", passwd="abcde" ]
```

**HELP NEEDED!!!**
Start a gateway discovery, and then send your (DEBUG-level) log file to the openHAB Community OpenWebNet thread to see if UPnP discovery is supported by your BTicino IP gateway.


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


## Channels

Devices support some of the following channels:

| Channel Type ID        | Item Type     | Description                                                             | Read/Write |
|------------------------|---------------|-------------------------------------------------------------------------|:----------:|
| switch                 | Switch        | To switch the device `ON` and `OFF`                                     |    R/W     |
| brightness             | Dimmer        | To adjust the brightness value (Percent,`ON`, `OFF`)                    |    R/W     |
| shutter                | Rollershutter | To activate roller shutters (`UP`, `DOWN`, `STOP`, Percent - [SEE NOTE](#notes-on-shutter-position)) |    R/W     |
| temperature            | Number        | The zone currently sensed temperature (°C)                              |     R      |
| targetTemperature      | Number        | The zone target temperature (°C). It considers `setPoint` but also `activeMode` and `localMode`  |      R     |
| thermoFunction         | String        | The zone set thermo function: `HEAT`, `COOL` or `GENERIC` (heating + cooling)     |      R     |
| heatingCoolingMode [*] | String        | The zone mode: `heat`, `cool`, `heatcool`, `off` (same as `thermoFunction`+ `off`, useful for Google Home integration)    |     R      |
| heating  [*]           | Switch        | `ON` if the zone heating valve is currently active (heating is On)      |     R      |
| cooling  [*]           | Switch        | `ON` if the zone cooling valve is currently active (cooling is On)      |     R      |
| activeMode             | String        | The zone current active mode (Operation Mode): `AUTO`, `MANUAL`, `PROTECTION`, `OFF`. It considers `setMode` and `localMode` (with priority)     |      R     |
| localMode              | String        | The zone current local mode, as set on the physical thermostat in the room: `-3/-2/-1/NORMAL/+1/+2/+3`, `PROTECTION`, or `OFF`  |      R     |
| setpointTemperature    | Number        | The zone setpoint temperature (°C), as set from Central Unit or openHAB |     R/W    |
| setMode                | String        | The zone set mode, as set from Central Unit or openHAB: `AUTO`, `MANUAL`, `PROTECTION`, `OFF`    |     R/W    |

[*] = advanced channel: in PaperUI can be shown from  *Thing config > Channel list > Show More* button. Link to an item by clicking on the channel blue button.


### Notes on Shutter position

For Percent commands and position feedback to work correctly, the `shutterRun` Thing config parameter must be configured equal to the time (in ms) to go from full UP to full DOWN.
It's possible to enter a value manually or set `shutterRun=AUTO` (default) to calibrate shutterRun parameter automatically the first time a Percent command is sent to the shutter: a *UP >> DOWN >> Position%* cycle will be performed automatically.

- if shutterRun is not set, or is set to AUTO but calibration has not been performed yet, then position estimation will remain `UNDEFINED`
- if shutterRun is set manually and too higher than the actual runtime, then position estimation will remain `UNDEFINED`: try to reduce shutterRun until you find the right value
- before adding/configuring roller shutter Things (or installing a binding update) it is suggested to have all roller shutters `UP`, otherwise the Percent command won’t work until the roller shutter is fully rolled up
- if the gateways gets disconnected then the binding cannot know anymore where the shutter was: if `shutterRun` is defined (and correct), then just roll the shutter all Up / Down and its position will be estimated again
- the shutter position is estimated based on UP/DOWN timing and therefore an error of ±2% is normal

## Google Home / Alexa Integration

Items created automatically with PaperUI (Simple Mode item linking: `Configuration > System > Item Linking > Simple mode > SAVE`) will integrate with Google Home / Alexa seamlessly as they will get automatically the proper tags. In particular items associated with these channels will have the following tags:

- `switch` / `brightness` channels will have the `"Lighting"` tag
- `temperature` channel will have the `"CurrentTemperature"` tag
- `setpointTemperature` channel will have the `"TargetTemperature"` tag
- `heatingCoolingMode` channel will have the `"homekit:HeatingCoolingMode"` tag

These are the tags supported so far.

It will be enough to link openHAB with myopenhab and Google Home / Alexa and you will be able to discover/control BTicino items.

Names used will be the names of the channels (Brightness, etc.) that cannot be changed in PaperUI, you can change names in the assistants.

However the most flexible configuration is obtained using `.items` file: see the examples below.

You will need to associate tags manually for items created using PaperUI (Simple Mode item linking de-activated) or for items created using `.items` file.
You can check which tags are set looking at the `tags` attribute in the REST API: http://openhabianpi:8080/rest/items.

See these docs and other threads in the community for more information about Google Home / Alexa integration and configuration:

- Google Assistant: https://www.openhab.org/docs/ecosystem/google-assistant/#setup-usage-on-google-assistant-app
- Amazon Alexa: https://www.openhab.org/docs/ecosystem/alexa/#general-configuration-instructions


## Full Example

### openwebnet.things:

```xtend
Bridge openwebnet:bus_gateway:mybridge  [ host="192.168.1.35", passwd="abcde" ] {
    bus_on_off_switch  myswitch       "Living room Light"       [ where="64#4#01" ]
    bus_dimmer         mydimmer       "Living room Dimmer"      [ where="24" ]
    bus_automation     myshutter      "Living room Shutter"     [ where="53", shutterRun="12000" ]
    bus_thermostat     lrthermostat   "Living room Thermostat"  [ where="1" ]
    bus_temp_sensor    exttempsensor  "External Sensor"         [ where="500" ]
}
``` 

```xtend
<TODO----- ZigBee Dongle configuration>
Bridge openwebnet:dongle:mydongle2  [serialPort="kkkkkkk"] {
    dimmer          myzigbeedimmer [ where="xxxxx"]
    on_off_switch   myzigbeeswitch [ where="yyyyy"]
}
```

### openwebnet.items:

The items in the example (Light, Dimmer, Thermostat) will be discovered by Google Home / Alexa if tags are configured like in the example.

```xtend
Switch         LR_Bus_Light    "Switch"                 <light>          (gLivingRoom)             [ "Lighting" ]  { channel="openwebnet:bus_on_off_switch:mybridge:myswitch:switch" }
Dimmer         LR_Bus_Dimmer   "Brightness [%.0f %%]"   <DimmableLight>  (gLivingRoom)             [ "Lighting" ]  { channel="openwebnet:bus_dimmer:mybridge:mydimmer:brightness" }
/* For Dimmers, use category DimmableLight to have Off/On switch in addition to the Percent slider in PaperUI */
Rollershutter  LR_Bus_Shutter  "Shutter [%.0f %%]"      <rollershutter>  (gShutters, gLivingRoom)                  { channel="openwebnet:bus_automation:mybridge:myshutter:shutter" }
Number         Ext_Temp        "Temperature [%.1f °C]"  <temperature>                                              { channel="openwebnet:bus_temp_sensor:mybridge:exttempsensor:temperature" }

/* Thermostat Setup (Google Home/Alexa require thermostat items to be grouped together) */
Group   gLR_Thermostat         "Living room Thermostat"                                      [ "Thermostat" ]
Number  LR_Temp                "Temperature [%.1f °C]"      <temperature>  (gLR_Thermostat)  [ "CurrentTemperature" ]          { channel="openwebnet:bus_thermostat:mybridge:lrthermostat:temperature" }
Number  LR_SetpointTemp        "Set Temperature [%.1f °C]"  <temperature>  (gLR_Thermostat)  [ "TargetTemperature" ]           { channel="openwebnet:bus_thermostat:mybridge:lrthermostat:setpointTemperature" }
String  LR_HeatingCoolingMode  "HeatingCoolingMode"                        (gLR_Thermostat)  [ "homekit:HeatingCoolingMode" ]  { channel="openwebnet:bus_thermostat:mybridge:lrthermostat:heatingCoolingMode" }
```

### openwebnet.sitemap

```xtend
<TODO----- example not available yet>
```

## Disclaimer

- This binding is not associated by any means with BTicino or Legrand companies
- The OpenWebNet protocol is maintained and Copyright by BTicino/Legrand. The documentation of the protocol if freely accessible for developers on the [MyOpen Community website - https://www.myopen-legrandgroup.com/developers](https://www.myopen-legrandgroup.com/developers/)
- OpenWebNet, MyHOME and MyHOME_Play are registered trademarks by BTicino/Legrand
- This binding uses `openwebnet-lib 0.9.x`, an OpenWebNet Java lib partly based on [openwebnet/rx-openwebnet](https://github.com/openwebnet/rx-openwebnet) client library by @niqdev, to support:
  - gateways and OWN frames for ZigBee
  - frame parsing
  - monitoring events from BUS

  The lib also uses few classes from the openHAB 1.x BTicino binding for socket handling and priority queues.

## Changelog

**v2.4.0-b8** - *NOT YET RELEASED*

- [FIX #17] now a disconnection from the gateway is detected within few minutes
- [FIX #10] now an automatic STOP command is sent when a new Position/UP/DOWN command is sent while already moving
- [FIX #18] at startup (for example after a power outage) the binding now tries periodically to connect to the BTicino Gateway
- [BUG] now a decimal setpointTemperature (21.5 °C) is sent as decimal to the thermostat and not as integer
- [FIX #20] added support for dimmerLevel100 levels when dimmers are changed from myhomescreen or touchscreen 
- [FIX #16] dimmers now save last state if switched off from openHAB
- [FIX] now Command Translation (1000#WHAT) is supported in Lighting
- [FIX] setpointTemperature now uses QuantityType (Number:Temperature item)

**v2.4.0-b7** - 01/09/2018

- [NEW, FIX #5] Initial support for `WHO=4` Thermoregulation on BUS. Currently supported: zones room thermostats and external (wireless) temperature sensors. Both heating and cooling functions are supported
- The binding is now available on the [Eclipse IoT Marketplace](https://marketplace.eclipse.org/content/openwebnet-2x-binding-testing)
- [BUG] corrected a bad bug on the connection part (originally taken from the BTicino 1.x binding) that caused loosing many monitoring (feedback) messages from the gateway when several messages were received together
- [FIX #13] now commands sent close in a short time are sent re-using the same socket connection to the gateway. This should improve connection stability and speed in general and in particular with older BUS gateways (e.g. MH202)
- [FIX] improved Shutter management and position estimation, thanks to previous 2 enhancements
- minimum requirement is now openHAB 2.3, which is needed to support measurements units like °C

**v2.4.0-b6** - 02/07/2018

- updated to openHAB 2.4.0 dev branch.
- [FIX #7] added support for inverted UP/DOWN automation commands for older USB ZigBee dongles
- [BUG] some switches were wrongly discovered as dimmers (now use only commands for device discovery)
- [FIX] added support for SCS/ZIGBEE_SHUTTER_SWITCH (515/513) device types
- [FIX] added support for F455 gateways using `*99*0##` command session

**v2.3.0-b5** - 26/05/2018

- [BUG #1] state monitoring from BUS (feedback) is no longer stopped if unsupported messages are received from BUS
- [FIX] automatic reconnect to BUS when connection is lost
- [NEW] support for Gateways with string passwords (HMAC authentication), like MyHOMEServer1 
- [NEW] support for `WHO=2` Automation (shutters), both on BUS and ZigBee, with position feedback and  goto Percent. It requires setting the shutter run-time in the thing configuration. Experimental auto-calibration of the run-time is also supported!

**v2.3.0-b4** - 09/04/2018

- first public release

## Known Issues
  
Known problems:

- In some cases dimmers connected to a F429 Dali-interface cannot be discovered, even if switched ON and dimmed. This looks like as a limitation of some gateways that do not report Dali devices states when requested

For a list of current open issues / features requests see [GitHub repo](https://github.com/mvalla/openhab-openwebnet/issues)

## Special thanks

Special thanks for helping on testing this binding go to: [@m4rk](https://community.openhab.org/u/m4rk/), [@enrico.mcc](https://community.openhab.org/u/enrico.mcc), [@bastler](https://community.openhab.org/u/bastler), [@k0nti](https://community.openhab.org/u/k0nti/), [@gilberto.cocchi](https://community.openhab.org/u/gilberto.cocchi/) and many others at the fantastic openHAB community!

