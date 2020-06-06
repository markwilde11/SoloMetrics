# Goal
I am aiming for a trainings aid in dinghies. A lot of sailors cannot train frequently with a second boat and have trouble judging their [VMG](https://en.wikipedia.org/wiki/Velocity_made_good). This project is aimed to have a digital feedback on your performance with (relative) low cost hardware. With a mobile phone to measure speed and boat-direction, an Ultrasonic for measuring windspeed and an e-ink screen to show the actual performance of the user.

## More Detail
To be able to judge your VMG you need to know what your performance was in previous sessions. The app aims to keep your data and calculate the VMG and store it. During post-processing the training session can be added to your performance  and you can calculate a [polar diagram](https://76trombones.wordpress.com/2009/10/17/polar-diagrams-vmg) for your personal performance. The aim is to feedback that data into the app so that the sailor can directly see how he is performing.

## Steps towards the Goal
There is quite a few steps to take to achieve this goal. Let's try to sum-up the first steps:
* DONE: Have a screen available to display realtime measurements and calculations
* DONE: Have a mobile app to connect the Anemometer ([Ultrasonic](https://calypsoinstruments.com/shop/product/ultrasonic-portable-7))
* DONE: Connect the mobile app to the Screen
* DONE: [feature] Add storage of the measurements ([#5][i5])
* [feature] Measure and store boat speed ([#7][i7])
* [feature] Measure and store boat direction ([#8][i8])
* 

[i5]: https://github.com/teamwildenberg/solometrics/issues/5
[i7]: https://github.com/teamwildenberg/solometrics/issues/7
[i8]: https://github.com/teamwildenberg/solometrics/issues/8

# Apps
This project contains two apps. An Arduino screen for showing wind measurements and an Android App for the computational power. The Android app is connecting to both the UltraSonic and the screen.

## Arduino 
Arduino setup for nRF52
- Arduino Board      - Adafruit Feather nRF52 Pro with myNewt Bootloader - nRF52832  
- E-Ink panel        - 2.9" e-Paper Display Modul mit SPI Interface 
- LiPolymer battery  - Polymer Lithium Ion Battery 2000mAh 
- USB cable          - https://www.kiwi-electronics.nl/adafruit/adafruit-cables/panel-mount-usb-b-naar-micro-b-kabel
- Feather Wing Proto - FeatherWing Proto - Prototyping Add-on For All Feather Boards [ADA-2884]

Actions
* TODO: Check for completeness 
* TODO: Add links to shop

## Android
[![Build Status](https://dev.azure.com/tw/Gps/_apis/build/status/GitHub%20-%20Tw%20-%20SoloMetrics%20-%20Build?branchName=master)](https://dev.azure.com/tw/Gps/_build/latest?definitionId=24&branchName=master)
[![Release Status](https://dev.azure.com/tw/Gps/_apis/build/status/GitHub%20-%20Tw%20-%20SoloMetrics%20-%20Release?branchName=master)](https://dev.azure.com/tw/Gps/_build/latest?definitionId=25&branchName=master)

Kotlin app for connecting the Ultrasonic and the Arduino Screen. The App is the center piece in your measurements. You can connect the App to the Ultrasonice winddevice and output all your measurements to the external arduino screen. You can use the app only to use the sensors of the Device, but my aim is to use it primarily with the Ultrasonic Measurement device. 

### Play Store:
The app is in a [close Alpha channel release](https://play.google.com/store/apps/details?id=nl.teamwildenberg.solometrics). If you are intersted, please contact me.

### Measurements:
The following sensors / measurements are calculated and stored:

Current:
- Ultrasonic Windspeed (knots)
- Ultrasonic Relative Wind Angle (degrees)
- Ultrasonic Boat Heading (degrees)

TODO:
- Android GPS Boat Direction (Degrees)
- Android GPS Lat / Lon / accurary
- Android GPS Boat Speed (knots)





## License
MIT
