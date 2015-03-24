# com.aware.plugin.io
Plugin for the AWARE framework (www.awareframework.com) for location type estimation.
The plugin was developed for mobile devices running Android operative system with version 2.3.3 or higher. 
The plugin is implemented for the AWARE Framework .
The plugin uses five sensors to infer the location type: battery, location, light, accelerometer, and magnetometer. 
The established bounds and weights for each sensor are shown in Table 3.
Notice that all sort of transport methods are categorized as outdoors.

#Provider
The plugin has a database provider where it stores permanently the collected data and the location type inferred, 
along with the elapsed time (time spent in the same location type) and the last update of the database.

#Context card
The context card is the part of the plugin where changes are shown to the user. This card is refreshed each three minutes. 
This interval allows the plugin to perform its computations and insert new values into the database, thus changes are noticeable.

#IO Alarm
In order to reduce the power consumption the plugin has alarm that turns on the sensors every two minutes. 
The sensors will stay active until they get the amount of samples needed, and then they will turn off.

#Battery
The plugin receives changes in the battery status, meaning if it is charging or not. If the phone is charging it is more 
probably in an indoor environment, with even higher probability if it is being charge from AC rather than USB. 

#Location
The plugin also receives changes in the location, taking into account only the location inferred by GPS technology, 
not the one from the network. Therefore, it is more probable that the user is indoors when the accuracy of the GPS location is 
lower, and outdoors when it is higher.

#Light
The light sensor is the slowest from the five sensors. Therefore it is the one that sets the next alarm when it finishes 
its computations. This sensor will process 10 samples and average them in order to minimize the impact of outliers in the 
measured value. The reliability of the light sensor depends on the hour of the day, due to that, the weight is multiply by a 
value given by the following function (that receives the hour of the day in 24-hour format as input):

                  f(x)=(-(x mod 12)/22)+1

This function is a straight line that gives 12 and 00 hours the highest value and decreases it along with the increment 
of the hour. This function is applied because, independently of the season and longitude where the device currently is, 
the sun will be in its highest point (brightest, therefore more luxes) at 12 and the lowest point (darkest, therefore less luxes).
The boundaries that conform the intervals are also multiply by those factors in order to adjust them to the sunlight.
 
#Accelerometer
Likewise the light sensor, the accelerometer is turn on by the IO Alarm approximately each two minutes. However, 
this sensor will process 20 samples (also to minimize the impact of outliers). This sensor can process more measurements 
because it is faster in measuring, and cannot be set up to do it at a slower rate.
The samples are taken as x, y and z-axis values. In consequence it is needed to calculate the vector 
(with the following function √(x^2+y^2+z^2 )). The samples are also averaged to minimize the impact of outliers.

#Magnetometer
This sensor is treated in the same manner than the accelerometer. It also takes 20 samples, calculating its vector and averaged 
them to minimize the effect of outliers.

#Inferring location type
The plugin has a decision matrix where the previous sensors store the location the inferred and the associated weight. 
The plugin will sum up the weights associated to indoors and outdoors, being the one with higher value the inferred by the plugin.
