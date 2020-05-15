#include <Arduino.h>

// FONT / SCREEN specific libs
#define ENABLE_GxEPD2_GFX 0
#include <GxEPD2_BW.h>
#include <Fonts/FreeMonoBold9pt7b.h>
#include <U8g2_for_Adafruit_GFX.h>

// BLE
#include <bluefruit.h>

// internal pin A7 for battery measurement
// external pin A6 for USB measurement 
#define VBATPIN A7
#define VUSBPIN A6
#define WAKEUPPIN 25
#define POWERLED 26
 

// SCREEN SPI initialization
GxEPD2_BW<GxEPD2_290, GxEPD2_290::HEIGHT> display(GxEPD2_290(/*CS=D8*/ 2, /*DC=D3*/ 3, /*RST=D4*/ 4, /*BUSY=D2*/ 5));

// CUSTOM 
// https://gist.github.com/stonehippo/ffbed2f32e1813f4019ecedd22062636
// - 7a2c5500-c492-4b71-ba1b-000000000001
const uint8_t SERVICE_UUID[16] = {0x1,0x0,0x0,0x0,0x0,0x0,0x1b,0xba,0x71,0x4b,0x92,0xc4,0x0,0x55,0x2c,0x7a};
const uint8_t CHARACTERISTIC1_UUID[16] = {0x2,0x0,0x0,0x0,0x0,0x0,0x1b,0xba,0x71,0x4b,0x92,0xc4,0x0,0x55,0x2c,0x7a};

BLEService screenService = BLEService(SERVICE_UUID);
BLECharacteristic screenMainCharacteristic = BLECharacteristic(CHARACTERISTIC1_UUID);

BLEDis bledis;    // DIS (Device Information Service) helper class instance
BLEBas blebas;    // BAS (Battery Service) helper class instance

int currentBatteryLevel = 0;
bool currentIsScreenConnected = false;
bool currentIsPowerConnected = false;

int currentMainValue = 361;
int currentBottomLeftValue = 361;
int currentTopRightValue = 361;
int currentBottomRightValue = 361;

byte screenValueArray[8] = {01,105,01,105,01,105,01,105};


struct FONT_DRAWBOX {
  uint16_t x;
  uint16_t y;
  uint16_t xOffset;
  uint16_t yOffset;
  bool isMain;
};

//const FONT_DRAWBOX mainDrawBox = {2, 2, 150, 78, true};
const FONT_DRAWBOX mainDrawBox = {2, 2, 80, 47, true};
const FONT_DRAWBOX bottomLeftDrawBox = {2, 80, 94, 47, false};
const FONT_DRAWBOX topRightDrawBox = {170, 2, 80, 47, false};
const FONT_DRAWBOX bottomRightDrawBox = {170, 80, 94, 47, false};

bool isScreenConnected = false;
uint16_t keepAliveCounter = 0;

// declare u8g2 struct.
U8G2_FOR_ADAFRUIT_GFX u8g2Fonts;

void setup() {
  // DEBUG serial monitor
  Serial.begin(115200);
  Serial.println();
  Serial.println("setup");

  pinMode(WAKEUPPIN, INPUT_PULLUP);
  attachInterrupt(WAKEUPPIN, wakeUpCallback, ISR_DEFERRED | CHANGE);
  pinMode(POWERLED, OUTPUT);
  digitalWrite(POWERLED, HIGH);
  
  
  
  setupBle();
  setupScreen();
  
  // Power implementation from her
  // https://github.com/adafruit/Adafruit_nRF52_Arduino/issues/165
  sd_power_mode_set(NRF_POWER_MODE_LOWPWR);
  sd_power_dcdc_mode_set(NRF_POWER_DCDC_ENABLE);

  screenMainCharacteristic.write(screenValueArray, 8);
}

void loop() {

  if (isScreenConnected){
    screenMainCharacteristic.read(&screenValueArray, 8);
    
    int newMainValue = parseIntValue(screenValueArray, 0);
    drawMainText(newMainValue, &currentMainValue, mainDrawBox);
    int newBottomLeftValue = parseIntValue(screenValueArray, 2);
    drawMainText(newBottomLeftValue, &currentBottomLeftValue, bottomLeftDrawBox);
    int newTopRightValue = parseIntValue(screenValueArray, 4);
    drawMainText(newTopRightValue, &currentTopRightValue, topRightDrawBox);
    int newBottomRightValue = parseIntValue(screenValueArray, 6);
    drawMainText(newBottomRightValue, &currentBottomRightValue, bottomRightDrawBox);
    
    keepAliveCounter ++;
    if (keepAliveCounter > 100){
      submitMainFeedbackNotification();
      keepAliveCounter = 0;
    }
  }
  
  bool isCharging = false;
  int batteryLevel = 0;
  determineBatteryLevel(batteryLevel, isCharging);
  blebas.write(batteryLevel); 
  drawBatteryLevel(batteryLevel);
  drawIcons(isCharging);
  delay(100);  
}

int parseIntValue(byte valueArray[], uint8_t offset){
  int value = valueArray[0 + offset] << 8;
  value |= valueArray[1 + offset];
  Serial.print("intValue: ");
  Serial.println(value);
  return value;
}


void setupBle(){
  Bluefruit.begin();
  Bluefruit.autoConnLed(false);
  Bluefruit.setTxPower(0);
  Bluefruit.setName("SoloScreenDuino");
  
  // Set the connect/disconnect callback handlers
  Bluefruit.Periph.setConnectCallback(connect_callback);
  Bluefruit.Periph.setDisconnectCallback(disconnect_callback);

  // Configure and Start the Device Information Service
  bledis.setManufacturer("TeamWildenberg");
  bledis.setModel("Bluefruit Feather52");
  bledis.begin();

  // Start the BLE Battery Service and set it to 100%
  blebas.begin();
  blebas.write(100);
  // Setup the Heart Rate Monitor service using
  // BLEService and BLECharacteristic classes
  // Setup the advertising packet(s)
  bleSetupHRM();
  bleStartAdv();
}

void bleStartAdv(void)
{
  Bluefruit.Advertising.addFlags(BLE_GAP_ADV_FLAGS_LE_ONLY_GENERAL_DISC_MODE);
  Bluefruit.Advertising.addTxPower();
  Bluefruit.Advertising.addService(screenService);

  // Secondary Scan Response packet (optional)
  // Since there is no room for 'Name' in Advertising packet
  Bluefruit.ScanResponse.addName();
  
  /* Start Advertising
   * - Enable auto advertising if disconnected
   * - Interval:  fast mode = 20 ms, slow mode = 152.5 ms
   * - Timeout for fast mode is 30 seconds
   * - Start(timeout) with timeout = 0 will advertise forever (until connected)
   * 
   * For recommended advertising interval
   * https://developer.apple.com/library/content/qa/qa1931/_index.html   
   */
  Bluefruit.Advertising.restartOnDisconnect(true);
  Bluefruit.Advertising.setInterval(200, 1000);    // in unit of 0.625 ms
  Bluefruit.Advertising.setFastTimeout(30);      // number of seconds in fast mode
  Bluefruit.Advertising.start(0);                // 0 = Don't stop advertising after n seconds  
}

void bleSetupHRM(void)
{
  screenService.begin();

  // Note: You must call .begin() on the BLEService before calling .begin() on
  // any characteristic(s) within that service definition.. Calling .begin() on
  // a BLECharacteristic will cause it to be added to the last BLEService that
  // was 'begin()'ed!

  // Configure the Heart Rate Measurement characteristic
  // See: https://www.bluetooth.com/specifications/gatt/viewer?attributeXmlFile=org.bluetooth.characteristic.heart_rate_measurement.xml
  screenMainCharacteristic.setProperties(CHR_PROPS_READ | CHR_PROPS_WRITE | CHR_PROPS_NOTIFY);
  screenMainCharacteristic.setPermission(SECMODE_OPEN, SECMODE_OPEN);
  screenMainCharacteristic.setUserDescriptor("Main screen pat");
  screenMainCharacteristic.setFixedLen(8);
  screenMainCharacteristic.setCccdWriteCallback(cccd_callback); 
  screenMainCharacteristic.begin();                  // Use .notify instead of .write!
}

void setupScreen(){
  currentMainValue = 361;
  currentBottomLeftValue = 361;
  currentTopRightValue = 361;
  currentBottomRightValue = 361;
  currentBatteryLevel = 0;
  currentIsScreenConnected = false;
  currentIsPowerConnected = false;
   
   // SCREEN INIT
  display.init(115200);
  display.setRotation(1);
  
  uint16_t bg = GxEPD_WHITE;
  uint16_t fg = GxEPD_BLACK;
  u8g2Fonts.begin(display);
  u8g2Fonts.setFontMode(1);                 // use u8g2 transparent mode (this is default)
  u8g2Fonts.setFontDirection(0);            // left to right (this is default)
  u8g2Fonts.setForegroundColor(fg);         // apply Adafruit GFX color
  u8g2Fonts.setBackgroundColor(bg);         // apply Adafruit GFX color

  display.firstPage();
  do{
    display.fillScreen(GxEPD_WHITE);
  }while (display.nextPage());
}



void determineBatteryLevel(int &batteryLevel, bool &isCharging){
  float measuredvusb = analogRead(VUSBPIN);
  measuredvusb *= 2;
  measuredvusb *= 3.3;
  measuredvusb /= 1024;
  measuredvusb *= 1.092; // calibrate the voltage divider (2*1000 ohm)

  float measuredvbat = analogRead(VBATPIN);
  measuredvbat *= 2;    // we divided by 2, so multiply back
  measuredvbat *= 3.3;  // Multiply by 3.3V, our reference voltage
  measuredvbat /= 1024; // convert to voltage
  measuredvbat *= 0.746; // calibrate (no clue, voltage divider broken? Corrected from 5.6v to 4.18)

  // max voltage drop = 1v (upper 4.2 and lower level 3.2)
  float drop = measuredvbat - 3.2;
  batteryLevel = (int)(drop * 100);
  isCharging = (measuredvusb > 4);
}


void drawMainText(int valueToDraw, int *currentValue, FONT_DRAWBOX box){
    if (valueToDraw != *currentValue){
      *currentValue = valueToDraw;
      Serial.print("Draw screen value: ");
      Serial.println(valueToDraw);
      
      String printString = String(valueToDraw);
      if (valueToDraw < 10){
        printString = "  " + printString;
      }else if (valueToDraw < 100){
        printString = " " + printString;
      }else if (valueToDraw > 360){
        printString = "   ";
      }

      display.setPartialWindow(box.x, box.y, box.xOffset, box.yOffset); 
      if (box.isMain){
        //u8g2Fonts.setFont(u8g2_font_logisoso78_tn);
        u8g2Fonts.setFont(u8g2_font_logisoso42_tn);
      }
      else{
        u8g2Fonts.setFont(u8g2_font_logisoso42_tn);
      }
      display.firstPage();
      do{
        u8g2Fonts.setCursor(box.x, box.y + box.yOffset); // start writing at this position
        u8g2Fonts.print(printString);
      }while (display.nextPage());
    }
}

void drawIcons(bool isCharging){
  if (  (isCharging != currentIsPowerConnected) 
    ||  (isScreenConnected != currentIsScreenConnected)) 
  {
    Serial.print("draw - Icons");

    currentIsPowerConnected = isCharging;
    currentIsScreenConnected = isScreenConnected;
 
    display.setPartialWindow(248, 112, 28, 16);
    display.firstPage();
    do{
      u8g2Fonts.setFont(u8g2_font_open_iconic_embedded_2x_t);
      if (isCharging){
        u8g2Fonts.drawGlyph(264, 128, 0x0043);
      }
      if (isScreenConnected){
        u8g2Fonts.drawGlyph(248, 128, 0x004A);
      } 
    }while (display.nextPage());
  }
}

void drawBatteryLevel(int batteryLevel){
  uint16_t dif = currentBatteryLevel - batteryLevel;
  
  if (dif > 3){
    currentBatteryLevel = batteryLevel;

    Serial.print("draw - Batterylevel - ");
     Serial.print(batteryLevel);
  
    display.setPartialWindow(278, 0, 20, 128);
    display.firstPage();
      do{
        
        u8g2Fonts.setFont(u8g2_font_open_iconic_embedded_2x_t);
        u8g2Fonts.drawGlyph(280, 127, 0x0049);
      
        // draw battery level
        drawBatterySegment(280, 10, (batteryLevel > 90));
        drawBatterySegment(280, 20, (batteryLevel > 80));
        drawBatterySegment(280, 30, (batteryLevel > 70));
        drawBatterySegment(280, 40, (batteryLevel > 60));
        drawBatterySegment(280, 50, (batteryLevel > 50));
        drawBatterySegment(280, 60, (batteryLevel > 40));
        drawBatterySegment(280, 70, (batteryLevel > 30));
        drawBatterySegment(280, 80, (batteryLevel > 20));
        drawBatterySegment(280, 90, (batteryLevel > 10));
        drawBatterySegment(280, 100, (batteryLevel > 0));
    }while (display.nextPage());
  }
}

void drawBatterySegment(int x, int y, bool isSolid){
  if (isSolid){
    display.fillRect(x, y, 14, 8, GxEPD_BLACK);
  }
  else{
    display.drawRect(x, y, 14, 8, GxEPD_BLACK);
  }
}

void connect_callback(uint16_t conn_handle)
{
  // Get the reference to current connection
  BLEConnection* connection = Bluefruit.Connection(conn_handle);

  char central_name[32] = { 0 };
  connection->getPeerName(central_name, sizeof(central_name));

  Serial.print("Connected to ");
  Serial.println(central_name);
  
  isScreenConnected = true;

}

void cccd_callback(uint16_t conn_hdl, BLECharacteristic* chr, uint16_t cccd_value)
{
    // Display the raw request packet
    Serial.print("CCCD Updated: ");
    //Serial.printBuffer(request->data, request->len);
    Serial.print(cccd_value);
    Serial.println("");

    // Check the characteristic this CCCD update is associated with in case
    // this handler is used for multiple CCCD records.
    if (chr->uuid == screenMainCharacteristic.uuid) {
        if (chr->notifyEnabled(conn_hdl)) {
            Serial.println("Main Screen 'Notify' enabled");
            submitMainFeedbackNotification();
        } else {
            Serial.println("Main Screen 'Notify' disabled");
        }
    }
}

/**
 * Callback invoked when a connection is dropped
 * @param conn_handle connection where this event happens
 * @param reason is a BLE_HCI_STATUS_CODE which can be found in ble_hci.h
 * https://github.com/adafruit/Adafruit_nRF52_Arduino/blob/master/cores/nRF5/nordic/softdevice/s140_nrf52_6.1.1_API/include/ble_hci.h
 */
void disconnect_callback(uint16_t conn_handle, uint8_t reason)
{
  (void) conn_handle;
  (void) reason;
  isScreenConnected = false;
  drawMainText(361, &currentMainValue, mainDrawBox);
  drawMainText(361, &currentBottomLeftValue, bottomLeftDrawBox);
  drawMainText(361, &currentTopRightValue, topRightDrawBox);
  drawMainText(361, &currentBottomRightValue, bottomRightDrawBox);
  screenMainCharacteristic.write(screenValueArray, 8);

//  setupScreen();
//  display.powerOff();
  Serial.println("disconnect callback");

}

void submitMainFeedbackNotification(){
  if ( screenMainCharacteristic.notify(screenValueArray, 8) ){
    char s[8];
  
    Serial.print("Notifying current screen value: "); 
    for(int i = 0; i < 8; i++)
    {
      if (screenValueArray[i] < 16) {
        Serial.print("0");
      }
      Serial.print(screenValueArray[i], HEX);
      Serial.print(" ");
    } 
    Serial.println("");
  }else{
    Serial.println("ERROR: Notify not set in the CCCD or not connected!");
  }
}

void wakeUpCallback(void)
{
  digitalWrite(POWERLED, LOW);
  // Serial.print("Power Off.");
  nrf_gpio_cfg_sense_input(WAKEUPPIN, NRF_GPIO_PIN_PULLUP, NRF_GPIO_PIN_SENSE_LOW);
  // sort of debounce
  // no more action needed, so Sleep will do.
  delay(200);
  setupScreen();
  sd_power_system_off();
}
