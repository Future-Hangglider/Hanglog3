# Hanglog3
Very rough and ready android app to read and log streamed position/orientation data from ESP32 device

The data can is stored in the phone memory and can be downloaded and plotted/analysed using the hacktrack 
program.

The code on the ESP32 is in the https://github.com/goatchurchprime/jupyter_micropython_developer_notebooks/tree/master/AirTemperatureTech
repository.  

There are plans to move it into here where it can more completely be maintained, simplified, and -- if necessary -- 
ported to C++.

For now, the ESP32 is the hotspot and the phone connects to its wifi and then taps into the UDP stream 
being sent out by its sensors (GPS, orientation, barometer)

We also log the same sensors from the phone, so we have a second version of the values to work with.

