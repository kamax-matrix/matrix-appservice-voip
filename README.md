# Matrix Bridge to Voice and Video networks
[![Build Status](https://travis-ci.org/kamax-matrix/matrix-appservice-voip.svg?branch=master)](https://travis-ci.org/kamax-matrix/matrix-appservice-voip)

[Requirements](#requirements)  
[Setup](#setup)  
[Integration](#integration)  
[Usage](#usage)

---

This is a Voice/Video/SMS bridge for Matrix using the Application Services (AS) API.

This software is currently in beta phase. It is considered stable enough to be used in production, but is not feature
complete and possibly rough around the edges. Therefore, your feedback and ideas are extremely welcome!

For any issue, idea, improvement, please help us by opening an issue or telling us about it on Matrix at
[#mxasd-voip:kamax.io](https://matrix.to/#/#mxasd-voip:kamax.io)

# Requirements
You will need Java 8 to build and run this bridge.

# Setup
Setup can either be done via downloading [the latest binary](https://github.com/kamax-matrix/matrix-appservice-voip/releases),
or by building the binary yourself.  
Java JRE 1.8 or higher is required to run the binary.  
Java JDK 1.8 or higher is required to build the binary.

## Steps overview
1. [Build the bridge](#build) (Optional)
0. [Configure the bridge](#configure)
0. [Run the bridge](#run)
0. [Configure your HS](#homeserver)
0. [Configure your VoIP backend](#voip-providers)
0. [See Usage instructions](#usage) to see how to interact with the bridge
0. Start calling!

## Build
If you would rather build the binary yourself rather than downloading the compiled one of the
[latest release](https://github.com/kamax-matrix/matrix-appservice-voip/releases):

Checkout the repo:
```bash
git clone https://github.com/kamax-matrix/matrix-appservice-voip.git
cd matrix-appservice-voip
```

Run the following command in the repo base directory. The runnable jar `mxasd-voip.jar` will be present in `./build/libs`:
```bash
./gradlew build
```

## Configure
Copy the default config file `application-sample.yaml` in the same directory as the jar file.  
The configuration file contains a detailed description for each possible configuration item.  
Edit it to suit your environment.

## Run
### Manual
Place the binary into the directory of your choice and run it.  
Example using `/opt/mxasd-voip`:
```bash
cd build/libs/mxasd-voip.jar /opt/mxasd-voip/
/opt/mxasd-voip/mxasd-voip.jar
```

# Integration
## Homeserver
### Synapse
Like any bridge, a registration file must be generated which will then be added to the HS config.  
Currently, there is no mechanism to automatically generate this config file.

You will find a working example at `registration-sample.yaml`, which you should copy at the same location as the Bridge configuration file.  
Configuration must match the various sections in the bridge config file.

Synapse can then be configured with:
```
app_service_config_files:
    - "/path/to/registration.yaml"
```

## VoIP providers
### FreeSWITCH
This bridge relies on the Verto module of FreeSWITCH which natively handles WebRTC.  
Edit the `providers.freeswitch.verto` section in the config file to suit your installation and provide the Websocket URI
and relevant credentials.

See [the dedicated HOWTO](freeswitch.md) to quickly get started.

# Usage
At this time, there is no easy way to invite someone by phone number in Matrix clients. Therefore, you'll need to compute
the Matrix ID that correspond to the phone number on your own.

The following step-by-step assumes you:
- Have `example.org` as your Matrix domain
- Have a user template with `_voip_%REMOTE_ID%`
- Want to place a call to `00123456789`

1. Manually compute the Matrix ID, which will be `@_voip_00123456789:example.org`
2. Create a new room/chat and invite that Matrix ID. This room must only have the virtual user and yourself as members.
3. Place a call, the device at `00123456789` should ring

# Support
On Matrix:
- #mxasd-voip:kamax.io
- #kamax-mxasd-voip:t2bot.io