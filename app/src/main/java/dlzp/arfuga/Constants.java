package dlzp.arfuga;

public class Constants {
    public static final String CbServiceUuid =              "d2e4b177-0dbe-4f21-b54a-2a81e4d23c4e";
    public static final String BoardLedCharUuid =           "1e7edaf6-ef50-44d4-8368-8becd241b5be";
    public static final String ButtonLeftCharUuid =         "2472170e-84b1-46aa-bdd7-f2861c021c2a";
    public static final String ButtonRightCharUuid =        "4bcf9ccf-2e59-4b61-a3b4-2cdc8f005d3b";
    public static final String ButtonLeftHandledCharUuid =  "1f01d834-af5e-4811-b4a6-95213ec9b193";
    public static final String ButtonRightHandledCharUuid = "0fb8f0e2-3e24-49ab-a1e1-6f5494f92a0e";
    public static final String ButtonLeftLedCharUuid =      "72c46fe5-2c30-47fb-994b-0f044b2fec4f";
    public static final String ButtonRightLedCharUuid =     "13d19bef-4512-4fef-84dc-7f251da6510b";

    public static final String ClientCharacteristicConfiguration = "00002902-0000-1000-8000-00805f9b34fb";

    public static final int ButtonPressTypePressSingle = 0b00;
    public static final int ButtonPressTypePressDouble = 0b01;
    public static final int ButtonPressTypeHoldShort = 0b10;
    public static final int ButtonPressTypeHoldLong = 0b11;

    public static final byte LedTimingLong =   (byte)0b11000000;
    public static final byte LedTimingMedium = (byte)0b10000000;
    public static final byte LedTimingShort =  (byte)0b01000000;
    public static final byte LedTimingBurst =  (byte)0b00000000;
    public static final byte LedTimingIgnore = LedTimingBurst;

    public static final String GaragePiCmdToggle = "toggle";
    public static final String GaragePiCmdTimed = "timedOperation";
    public static final String GaragePiCmdStatus = "status";
    public static final String GaragePiCmdClose = "close";
    public static final String GaragePiCmdOpen = "open";
    public static final String GaragePiCmdDisable = "disable";
    public static final String GaragePiCmdEnable = "enable";
    public static final String GaragePiCmdDisenable = "disenable";
    public static final String GaragePiCmdLoud = "loud";
    public static final String GaragePiCmdQuiet = "quiet";
}
