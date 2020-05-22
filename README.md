# Goal
I am aiming for a trainings aid in dinghies. A lot of sailors cannot train frequently with a second boat and have trouble judging their [VMG](https://en.wikipedia.org/wiki/Velocity_made_good). This project is aimed to have a digital feedback on your performance with (relative) low cost hardware. With a mobile phone to measure speed and boat-direction, an Ultrasonic for measuring windspeed and an e-ink screen to show the actual performance of the user.

## More Detail
To be able to judge your VMG you need to know what your performance was in previous sessions. The app aims to keep your data and calculate the VMG and store it. During post-processing the training session can be added to your performance  and you can calculate a [polar diagram](https://76trombones.wordpress.com/2009/10/17/polar-diagrams-vmg) for your personal performance. The aim is to feedback that data into the app so that the sailor can directly see how he is performing.

## Steps towards the Goal
There is quite a few steps to take to achieve this goal. Let's try to sum-up the first steps:
* Have a screen available to display realtime measurements and calculations
* Have a mobile app to connect the Anemometer ([Ultrasonic](https://calypsoinstruments.com/shop/product/ultrasonic-portable-7))
* Connect the mobile app to the Screen
* [feature] Add storage of the measurements ([#5][i5])
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
[![Build Status](https://dev.azure.com/tw/Gps/_apis/build/status/kotlin-android-release?branchName=master)](https://dev.azure.com/tw/Gps/_build/latest?definitionId=18&branchName=master)

Kotlin app for connecting the Ultrasonic and the Arduino Screen.



## License
MIT
