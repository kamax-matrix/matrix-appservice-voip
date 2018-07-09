# FreeSWITCH integration HOWTO
This HOWTO will show you how to:
- Install FreeSWITCH from scratch
- Configure FreeSWITCH to bare-minimum level
- Integrate your SIP provider to FreeSWITCH
- Configure the bridge to connect to FreeSWITCH
- Make a call from Matrix to the regular phone network

**IMPORTANT:** This HOWTO will not make any attempt in creating a secure install, only to give you the ability to test the bridge.
## Requirements
- Debian 8 system
- Ability to support incoming UDP on port 5080, relayed to the system

## Configure
### FreeSWITCH
#### Add the FreeSWITCH repo GPG key for APT
```bash
wget -O - https://files.freeswitch.org/repo/deb/debian/freeswitch_archive_g0.pub | apt-key add -
```

#### Add the repos
```bash
echo "deb http://files.freeswitch.org/repo/deb/freeswitch-1.6/ jessie main" > /etc/apt/sources.list.d/freeswitch.list
echo "deb http://ftp.debian.org/debian jessie-backports main" > /etc/apt/sources.list.d/debian-backports.list
```

#### Install FreeSWITCH and its Verto and RTC modules, for WebRTC support
```bash
apt-get update && apt-get install -y freeswitch-meta-all freeswitch-mod-verto freeswitch-mod-rtc
```

#### Change default password
**IMPORTANT:** If you do not do this, FreeSWITCH will delay each call!

Edit `/etc/freeswitch/vars.xml` and set another default password password by replacing `1234` on this line:
```xml
<X-PRE-PROCESS cmd="set" data="default_password=1234"/>
```

#### Add SIP provider
Create a file `/etc/freeswitch/sip_profiles/external/default.xml` with following content, adapting `username`, `real` and `password` as needed:
```xml
<include>
  <gateway name="default">
    <param name="username" value="00123456789"/>
    <param name="realm" value="sip.provider.example.com"/>
    <param name="password" value="abc@abc"/>
    <param name="register" value="true"/>
    <param name="register-transport" value="udp"/>
  </gateway>
</include>
```

#### Make SIP provider as default gateway
Edit `/etc/freeswitch/directory/default.xml` and replace `${{default_provider}}` by `default` (gateway name) in the following line:
```xml
      <variable name="default_gateway" value="$${default_provider}"/>
```

#### Fix dialplan so it works by default
Edit `/etc/freeswitch/dialplan/default/01_example.com.xml` and replace the following line:
```xml
    <condition field="destination_number" expression="^(011\d+)$">
```
with
```xml
    <condition field="destination_number" expression="^(\d+)$">
```

#### Restart Freeswitch
```bash
systemctl restart freeswitch
```

### VoIP bridge
Given the following example values, adapt as needed:
- LAN IP of the FreeSWITCH system: `10.1.2.3`
- New default password in FreeSWITCH: `12345`
- Your Matrix User ID is `@user1:domain.tld`

In `application.yaml`, set:
```yaml
providers.freeswitch.verto:
    url: 'ws://10.1.2.3:8081'
    login: '1000'
    password: '12345'

bridge.mapping.users:
  user1: '1000'
```

Restart the bridge

## Use
If you wanted to call `00123456789`, assuming you've respected default values in [README.md](README.md):
- Create a new room
- Invite `@_voip_00123456789:domain.tld`
- Hit the call button
- The call should go through

## Troubleshoot
TBC
