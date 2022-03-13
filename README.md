# Catalytic Converter Theft Alarm

This Android app is intended to be run on an unused Android phone left 24/7 inside a car and acts as a catalytic converter theft detection device.  The phone should be connected to the car's power outlet and configured to minimize power consumption.

The app uses the accelerometer sensor to detect if the car's engine is running (by detecting continuous low-level vibration). If the engine is off and a tilt is detected, the app will post a message to a preconfigured HTTP endpoint, whereupon appropriate actions can be taken such as raising an alarm.
