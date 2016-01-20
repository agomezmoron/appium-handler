# appium-handler
Appium handler to work with the [Appium](http://appium.io/) Java client and configure it to test native or hybrid applications.

This library offers a wrapper of the Appium Java client, following the Selenium Driver interface like returning null if the element/s don't/doesn't exist. Also offer some additional functionalities like a method called *isDriverReadyToTest()* that notifies if the driver is ready to test the app (depending on if it's hybrid or not).

The library also creates an Android or iOS driver depending on the *platformName* capability.

You can add the library into your maven project adding the repository and the dependency like:

```
  <repositories>
		<repository>
			<id>appium-handler</id>
			<name>AppiumHandler library built by agomezmoron</name>
			<url>https://raw.github.com/agomezmoron/appium-handler/mvn-repo</url>
		</repository>
	</repositories>
	...
	<dependency>
			<groupId>com.agomezmoron</groupId>
			<artifactId>appium-handler</artifactId>
			<version>0.0.1</version>
		</dependency>
```
An example of the library usage is:
    
    /**
     * Example for TestNG.
     **/
    @BeforeSuite
    public void setUpAppium() throws MalformedURLException {

        final String URL_STRING = "http://127.0.0.1:4723/wd/hub";

        URL url = new URL(URL_STRING);

        DesiredCapabilities capabilities = new DesiredCapabilities();
        capabilities.setCapability("platformName", "Android");
        capabilities.setCapability("deviceName", "SamsungGalaxyS5");
        capabilities.setCapability("app", "/home/agomezmoron/Descargas/demo.apk");
        // flag to know if the app is hybrid or not
        capabilities.setCapability("appHybrid", true);

        driver = AppiumHandledDriver.buildInstance(url, capabilities);
        // avoiding to perform any test if the driver is not ready        
        assertTrue(driver.isDriverReadyToTest());
    }

If you want to contribute to complete the library, feel free to contact me.
