# Arfuga

Named from an acronym standing for "Arduino-FuelTracker-GaragePi', this is a multipurpose Android application primarily used for smart home + IoT communication and event triggering, primarily used when driving or idling in a vehicle.

## Arduino

Using Bluetooth Low-Energy (BLE) and Android Companion Device technologies, the most comprehensive component of Arfuga is its association and communication with a nearby "N33ble1" Arduino device. 

For this project, an [Arduino Nano 33 BLE](https://store.arduino.cc/products/arduino-nano-33-ble) (of which, the 1st (and currently only) external board used - hence the lovingly crafted acronym N33ble1) was configured with two momentary push buttons and installed into the dash of the vehicle.

When the vehicle comes online, the 3.3-volt N33ble1 (powered through the vehicle's 5v console USB port) will advertise itself over BLE. After one-time association, Arfuga will silently connect and await user-input through N33ble's two momentary buttons, both of which are easily accessible by the driver. Various actions are taken within Arfuga once user events are received through N33ble1.

N33ble1's Arduino code is kept in the [arfuga-n33ble1](https://github.com/derekpock/arfuga-n33ble1) repository.

## Fuel Tracker

Arfuga also provides a basic form interface for entering fuel refilling statistics concerning the vehicle. The user manually enters information into this form such as gallons purchased, price per gallon, miles driven, and other stats tracked by the vehicle's internal computers.

This information, when prompted by the user, is sent to a non-standard external server for storing and record keeping. The external server's logic is not currently shared anywhere.

## GaragePi

Finally, Arfuga also provides an interface for communicating with a remote garage door controlling device called GaragePi. The user can select various actions from the GaragePi tab in order to operate or get statuses from the garage door. 

As currently configured, one of the momentary buttons connected to N33ble1 and transmitted to Arfuga sends various commands to the remote GaragePi server in order to easily and seamlessly, without opening the app, control the garage door from a push of a button - with no RF distance or signal requirements! 

Arfuga currently sends commands to the remote GaragePi server such as 'status' (is the door currently closed/open), 'toggle' (simulate the garage door's button press once), and 'timedOperation' (open the door if closed, and after a short delay, close the door), among other operations. The remote GaragePi server code is not currently shared anywhere.

# Setting Up

This section is WIP, but there's some things missing from this repository that you'll need right away before attempting to build anything.

## Secrets

Certain secret addresses, keys, identifiers, and what-have-you are not provided in this repository. To build, you will need to create a new resources file in Android Studio at the following path with various keys and addresses that are specific to your configuration:

`/app/src/main/res/values/secrets.xml`

```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <string name="HttpKey">MyKeyHere</string>
    <string name="HttpHost">www.MyServerHost.com</string>
    <integer name="HttpGaragePiPort">12345</integer>
    <integer name="HttpFuelTrackerPort">12346</integer>
    <string name="N33ble1Address">00:00:00:00:00:00</string>
</resources>
```

Their uses can be deduced from the code, but both the FuelTracker and the GaragePi endpoints both reside on the same remote server, accessible via the HttpHost and at their respective ports.

HttpKey is a secret phrase used to provide rudimentary authentication when communicating with the GaragePi.

N33ble1Address is the Bluetooth Address of the N33ble1 Arduino device running the [arfuga-n33ble1](https://github.com/derekpock/arfuga-n33ble1) code. This address can easily be discovered using any standard BLE scanner app when your N33ble1 is powered.