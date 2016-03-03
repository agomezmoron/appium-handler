/**
 *
 * The MIT License (MIT)
 *
 * Copyright (c) 2016 Alejandro Gómez Morón
 * 
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * 
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 * 
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.agomezmoron.appiumhandler;

import java.io.File;
import java.net.URL;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;
import org.openqa.selenium.By;
import org.openqa.selenium.Capabilities;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.Platform;
import org.openqa.selenium.ScreenOrientation;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebDriver.TargetLocator;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.html5.Location;
import org.openqa.selenium.remote.DesiredCapabilities;
import org.openqa.selenium.remote.ExecuteMethod;
import org.openqa.selenium.remote.RemoteWebDriver;

import com.google.gson.JsonObject;

import io.appium.java_client.AppiumDriver;
import io.appium.java_client.MobileElement;
import io.appium.java_client.MultiTouchAction;
import io.appium.java_client.TouchAction;
import io.appium.java_client.android.AndroidDriver;
import io.appium.java_client.ios.IOSDriver;

/**
 * Appium handler driver to work with any appium implementation iOS/Android and working with
 * the app independent of the app (native or hybrid).
 * 
 * @author Alejandro Gomez <agommor@gmail.com>
 * @author Ivan Gomez de Leon <igomez@emergya.com>
 *
 */
public class AppiumHandledDriver {

    /**
     * Log instance.
     */
    private final static Logger LOGGER = Logger.getLogger(AppiumHandledDriver.class);

    /**
     * Key to be used in the {@link DesiredCapabilities} checking.
     */
    private static String PLATFORM_TYPE_KEY = "platformName";

    /**
     * Key to be used in the {@link DesiredCapabilities} checking.
     */
    private static String APP_KEY = "app";

    /**
     * Key to be used in the {@link DesiredCapabilities} checking.
     */
    private static String APP_HYBRID = "appHybrid";

    /**
     * Parameter to have always the main window.
     */
    private String mainWindow;

    /**
     * Involved instance (decorator pattern).
     */
    private AppiumDriver<MobileElement> driver;

    /**
     * Flag to know if we're testing an hybrid app.
     */
    private boolean isAnHybridApp;

    /**
     * Builder method to create {@link AppiumHandledDriver} instances.
     * @param remoteAddress to be used.
     * @param desiredCapabilities to be used.
     * @return an {@link AppiumHandledDriver} instance with the custom implementation.
     */
    public static AppiumHandledDriver buildInstance(URL remoteAddress, DesiredCapabilities desiredCapabilities) {
        AppiumHandledDriver instance = null;

        // getting app path (if it's exists)
        Object appCapability = desiredCapabilities.getCapability(APP_KEY);
        if (appCapability != null && appCapability instanceof String) {
            String appPath = (String) appCapability;
            File file = new File(appPath);
            if (file.exists()) {
                desiredCapabilities.setCapability(APP_KEY, file.getAbsolutePath());
            } else {
                LOGGER.error("The app was defined but it cannot be found in " + appPath);
            }
        }

        AppiumDriver<MobileElement> driver = null;

        // building the instance
        if (isIOS(desiredCapabilities)) {
            driver = new IOSDriver<MobileElement>(remoteAddress, desiredCapabilities);
        } else if (isAndroid(desiredCapabilities)) {
            driver = new AndroidDriver<MobileElement>(remoteAddress, desiredCapabilities);
        } else {
            // TODO: work on it. Nowadays just iOS and android are supported by this handler.
            driver = new AndroidDriver<MobileElement>(remoteAddress, desiredCapabilities);
        }

        // implicit wait for slow devices
        driver.manage().timeouts().implicitlyWait(35, TimeUnit.SECONDS);
        Boolean isHybrid = false;
        Object appHybrid = desiredCapabilities.getCapability(APP_HYBRID);
        if (appHybrid != null && appHybrid instanceof Boolean) {
            isHybrid = (Boolean) appHybrid;
            if (isHybrid) {
                // if the app is hybrid, we have to wait until the WEBVIEW context handler exists
                driver = switchToWebViewContext(driver);
            }
        }
        // now the driver is configured, we create the wrapper
        instance = new AppiumHandledDriver(driver, isHybrid);
        return instance;
    }

    /**
     * This method switches to webview context (for hybrid apps).
     * @param driver to be switched to.
     * @param 
     * @return modified driver instance.
     */
    private static AppiumDriver<MobileElement> switchToWebViewContext(AppiumDriver<MobileElement> driver) {
        long start = new Date().getTime();
        long end = new Date().getTime();
        long limit = 15; // waiting no more than 15 seconds to switch to a WEBVIEW context
        boolean switched = false;
        int maxRetries = 5;
        // for HYBRID APPS, switching the context
        do {
            sleepFor(1);
            Set<String> contextHandles = driver.getContextHandles();
            for (String context : contextHandles) {
                if (context.contains("WEBVIEW")) {
                    // the context change needs some extra time
                    int retries = 0;
                    do {
                        sleepFor(5);
                        try {
                            driver.context(context);
                        } catch (Exception ex) {
                            LOGGER.warn("An error occurred switching the context. Trying again...");
                        }
                        retries++;
                    } while (!driver.getContext().contains("WEBVIEW") && retries < maxRetries);
                    switched = true;
                }
            }
            end = new Date().getTime();
        } while (!switched && (start + (limit * 1000)) > end);

        if (!switched) {
            LOGGER.error("After waiting for " + limit
                    + " seconds, the driver couldn't switched to the WEBVIEW context, so the test of the hybrid application will failed!");
        }
        return driver;
    }

    /**
     * Method to know if the driver is ready to test hybrid / native apps.
     * @return true if the driver is well configured or false otherwise.
     */
    public boolean isDriverReadyToTest() {
        boolean ready = false;
        if (this.driver != null) {
            if (this.isAnHybridApp) {
                if (this.getContext().contains("WEBVIEW")) {
                    ready = true;
                }
            } else {
                if (!this.getContext().contains("WEBVIEW")) {
                    ready = true;
                }
            }
        }
        return ready;
    }

    /**
     * Private constructor to avoid instances creation without using the buildInstance method.
     * @param driver an {@link AppiumDriver} instance.
     * @param isHybrid flag to know if it's an hybrid app.
     */
    private AppiumHandledDriver(AppiumDriver<MobileElement> driver, Boolean isHybridApp) {
        this.driver = driver;
        this.isAnHybridApp = isHybridApp;
        this.mainWindow = this.driver.getWindowHandle();
    }

    /**
     * It switches to the main window if it's an hybrid app.
     */
    public void switchToMainWindow() {
        if (this.isAnHybridApp && StringUtils.isNotBlank(this.mainWindow)
                && !this.driver.getWindowHandle().equals(this.mainWindow)) {
            this.driver.switchTo().window(this.mainWindow);
        }
    }

    /**
     * It checks if it's an iOS platform.
     * @param desiredCapabilities to check if the SO is iOS.
     * @return true if it's an iOS testing.
     */
    private static boolean isIOS(Capabilities desiredCapabilities) {
        return is(desiredCapabilities, "ios");
    }

    /**
     * It checks if it's an Android platform.
     * @param desiredCapabilities to check if the SO is iOS.
     * @return true if it's an iOS testing.
     */
    private static boolean isAndroid(Capabilities desiredCapabilities) {
        return is(desiredCapabilities, Platform.ANDROID.name());
    }

    /**
     * It checks if it's an iOS platform.
     * @param desiredCapabilities to check if the SO is iOS.
     * @param type to check.
     * @return true if it's an iOS testing.
     */
    private static boolean is(Capabilities desiredCapabilities, String type) {
        boolean is = false;
        if (desiredCapabilities != null) {
            Object capability = desiredCapabilities.getCapability(PLATFORM_TYPE_KEY);
            if (capability != null && capability instanceof String) {
                if (type != null && type.equalsIgnoreCase(((String) capability))) {
                    is = true;
                }
            }
        }
        return is;
    }

    /**
     * This method waits for the {@link MobileElement} described by the {@By} selector with a timeout of seconds.
     * @param selector to get the element.
     * @param seconds to wait for (timeout).
     * @param message to send to the log if something happens.
     */
    public void waitFor(By selector, long seconds, String message) {
        LOGGER.info("Waiting for " + selector.toString());
        long start = new Date().getTime();
        long end = new Date().getTime();
        MobileElement element = null;
        do {
            element = this.findElement(selector);
            end = new Date().getTime();
        } while (element == null && (start + (seconds * 1000)) > end);

        if (element == null) {
            if (message != null && "".equals(message.trim())) {
                LOGGER.error("After waiting " + seconds + " seconds for the element " + selector.toString()
                        + ", the element is missing!. Custom message: " + message);
            } else {
                LOGGER.error("After waiting " + seconds + " seconds for the element " + selector.toString()
                        + ", the element is missing!");
            }
        }
    }

    /**
     * This method waits for the {@link MobileElement} described by the {@By} selector with a timeout of seconds.
     * @param selector to get the element.
     * @param seconds to wait for (timeout).
     */
    public void waitFor(By selector, long seconds) {
        this.waitFor(selector, seconds, null);
    }

    /**
     * This method waits for the {@link MobileElement} until it's visible described by the {@By} selector with a timeout of seconds.
     * @param selector to get the element.
     * @param seconds to wait for (timeout).
     * @param message to send to the log if something happens.
     */
    public void waitUntilVisible(By selector, long seconds, String message) {
        LOGGER.info("Waiting for " + selector.toString());
        this.waitFor(selector, seconds, message);
        MobileElement element = this.findElement(selector);
        if (element != null && !element.isDisplayed()) {
            LOGGER.error("After waiting " + seconds + " seconds for the element " + selector.toString()
                    + " exists in the DOM but is not displayed.");
        }
    }

    /**
     * It sleeps the driver for n seconds.
     * @param seconds to be slept.
     */
    public void wait(int seconds) {
        long start = new Date().getTime();
        try {
            driver.wait(seconds * 1000);
        } catch (InterruptedException e) {
            long end = new Date().getTime();
            do {
                end = new Date().getTime();
            } while ((start + (seconds * 1000)) > end);
        }
    }

    /**
     * It sleeps the process for n seconds.
     * @param seconds to be slept.
     */
    public void sleep(long seconds) {
        sleepFor(seconds);
    }

    /**
     * It sleeps the process for n seconds.
     * @param seconds to be slept.
     */
    public static void sleepFor(long seconds) {
        long start = new Date().getTime();
        try {
            Thread.sleep(seconds * 1000);
        } catch (InterruptedException e) {
            long end = new Date().getTime();
            do {
                end = new Date().getTime();
            } while ((start + (seconds * 1000)) > end);
        }
    }

    /**
     * @see {@link AppiumDriver#findElements(By)}.
     */
    public List<MobileElement> findElements(By by) {
        List<MobileElement> elements = null;
        try {
            elements = driver.findElements(by);
        } catch (Exception ex) {
            elements = new ArrayList<MobileElement>();
        }
        return elements;
    }

    /**
     * @see {@link AppiumDriver#findElementsById(String)}.
     */
    public List<MobileElement> findElementsById(String id) {
        List<MobileElement> elements = null;
        try {
            elements = driver.findElementsById(id);
        } catch (Exception ex) {
            elements = new ArrayList<MobileElement>();
        }
        return elements;
    }

    /**
     * @see {@link AppiumDriver#findElementsByLinkText(String)}.
     */
    public List<MobileElement> findElementsByLinkText(String using) {
        List<MobileElement> elements = null;
        try {
            elements = driver.findElementsByLinkText(using);
        } catch (Exception ex) {
            elements = new ArrayList<MobileElement>();
        }
        return elements;
    }

    /**
     * @see {@link AppiumDriver#findElementsByPartialLinkText(String)}.
     */
    public List<MobileElement> findElementsByPartialLinkText(String using) {
        List<MobileElement> elements = null;
        try {
            elements = driver.findElementsByPartialLinkText(using);
        } catch (Exception ex) {
            elements = new ArrayList<MobileElement>();
        }
        return elements;
    }

    /**
     * @see {@link AppiumDriver#findElementsByTagName(String)}.
     */
    public List<MobileElement> findElementsByTagName(String using) {
        List<MobileElement> elements = null;
        try {
            elements = driver.findElementsByTagName(using);
        } catch (Exception ex) {
            elements = new ArrayList<MobileElement>();
        }
        return elements;
    }

    /**
     * @see {@link AppiumDriver#findElementsByName(String)}.
     */
    public List<MobileElement> findElementsByName(String using) {
        List<MobileElement> elements = null;
        try {
            elements = driver.findElementsByName(using);
        } catch (Exception ex) {
            elements = new ArrayList<MobileElement>();
        }
        return elements;
    }

    /**
     * @see {@link AppiumDriver#findElementsByClassName(String)}.
     */
    public List<MobileElement> findElementsByClassName(String using) {
        List<MobileElement> elements = null;
        try {
            elements = driver.findElementsByClassName(using);
        } catch (Exception ex) {
            elements = new ArrayList<MobileElement>();
        }
        return elements;
    }

    /**
     * @see {@link AppiumDriver#findElementsByCssSelector(String)}.
     */
    public List<MobileElement> findElementsByCssSelector(String using) {
        List<MobileElement> elements = null;
        try {
            elements = driver.findElementsByCssSelector(using);
        } catch (Exception ex) {
            elements = new ArrayList<MobileElement>();
        }
        return elements;
    }

    /**
     * @see {@link AppiumDriver#findElementsByXPath(String)}.
     */
    public List<MobileElement> findElementsByXPath(String using) {
        List<MobileElement> elements = null;
        try {
            elements = driver.findElementsByXPath(using);
        } catch (Exception ex) {
            elements = new ArrayList<MobileElement>();
        }
        return elements;
    }

    /**
     * @see {@link AppiumDriver#findElementsByAccessibilityId(String)}.
     */
    public List<MobileElement> findElementsByAccessibilityId(String using) {
        List<MobileElement> elements = null;
        try {
            elements = driver.findElementsByAccessibilityId(using);
        } catch (Exception ex) {
            elements = new ArrayList<MobileElement>();
        }
        return elements;
    }

    /**
     * @see {@link DefaultGenericMobileDriver#findElement(By)}.
     */
    public MobileElement findElement(By by) {
        MobileElement element;
        try {
            element = driver.findElement(by);
        } catch (Exception ex) {
            element = null;
        }
        return element;
    }

    /**
     * @see {@link DefaultGenericMobileDriver#findElementById(String)}.
     */
    public MobileElement findElementById(String id) {
        MobileElement element;
        try {
            element = driver.findElementById(id);
        } catch (Exception ex) {
            element = null;
        }
        return element;
    }

    /**
     * @see {@link DefaultGenericMobileDriver#findElementByLinkText(String)}.
     */
    public MobileElement findElementByLinkText(String using) {
        MobileElement element;
        try {
            element = driver.findElementByLinkText(using);
        } catch (Exception ex) {
            element = null;
        }
        return element;
    }

    /**
     * @see {@link DefaultGenericMobileDriver#findElementByPartialLinkText(String)}.
     */
    public MobileElement findElementByPartialLinkText(String using) {
        MobileElement element;
        try {
            element = driver.findElementByPartialLinkText(using);
        } catch (Exception ex) {
            element = null;
        }
        return element;
    }

    /**
     * @see {@link DefaultGenericMobileDriver#findElementByTagName(String)}.
     */
    public MobileElement findElementByTagName(String using) {
        MobileElement element;
        try {
            element = driver.findElementByTagName(using);
        } catch (Exception ex) {
            element = null;
        }
        return element;
    }

    /**
     * @see {@link DefaultGenericMobileDriver#findElementByName(String)}.
     */
    public MobileElement findElementByName(String using) {
        MobileElement element;
        try {
            element = driver.findElementByName(using);
        } catch (Exception ex) {
            element = null;
        }
        return element;
    }

    /**
     * @see {@link AppiumDriver#getExecuteMethod()}.
     */
    public ExecuteMethod getExecuteMethod() {
        return driver.getExecuteMethod();
    }

    /**
     * @see {@link AppiumDriver#resetApp()}.
     */
    public void resetApp() {
        driver.resetApp();
    }

    /**
     * @see {@link AppiumDriver#isAppInstalled(String)}.
     */
    public boolean isAppInstalled(String bundleId) {
        return driver.isAppInstalled(bundleId);
    }

    /**
     * @see {@link AppiumDriver#installApp(String)}.
     */
    public void installApp(String appPath) {
        driver.installApp(appPath);
    }

    /**
     * @see {@link AppiumDriver#removeApp(String)}.
     */
    public void removeApp(String bundleId) {
        driver.removeApp(bundleId);
    }

    /**
     * @see {@link AppiumDriver#launchApp()}.
     */
    public void launchApp() {
        driver.launchApp();
    }

    /**
     * @see {@link AppiumDriver#closeApp()}.
     */
    public void closeApp() {
        driver.closeApp();
    }

    /**
     * @see {@link AppiumDriver#runAppInBackground(int)}.
     */
    public void runAppInBackground(int seconds) {
        driver.runAppInBackground(seconds);
    }

    /**
     * @see {@link AppiumDriver#hideKeyboard()}.
     */
    public void hideKeyboard() {
        driver.hideKeyboard();
    }

    /**
     * @see {@link AppiumDriver#pullFile(String)}.
     */
    public byte[] pullFile(String remotePath) {
        return driver.pullFile(remotePath);
    }

    /**
     * @see {@link AppiumDriver#pullFolder(String)}.
     */
    public byte[] pullFolder(String remotePath) {
        return driver.pullFolder(remotePath);
    }

    /**
     * @see {@link AppiumDriver#performTouchAction(TouchAction)}.
     */
    public TouchAction performTouchAction(TouchAction touchAction) {
        return driver.performTouchAction(touchAction);
    }

    /**
     * @see {@link AppiumDriver#performMultiTouchAction(MultiTouchAction)}.
     */
    public void performMultiTouchAction(MultiTouchAction multiAction) {
        driver.performMultiTouchAction(multiAction);
    }

    /**
     * This method is the same than {@link AppiumHandledDriver#tap(int, WebElement, int)} but using a {@link MobileElement} object.
     */
    public void tap(int fingers, MobileElement element, int duration) {
        int xPosition = element.getLocation().getX() + element.getSize().getWidth()/2;
        int yPosition = element.getLocation().getY() + element.getSize().getHeight()/2;
        this.tap(fingers, xPosition, yPosition, duration);
    }

    /**
     * @see {@link AppiumDriver#tap(int, WebElement, int)}.
     */
    public void tap(int fingers, WebElement element, int duration) {
        driver.tap(fingers, element, duration);
    }

    /**
     * @see {@link AppiumDriver#tap(int, int, int, int)}.
     */
    public void tap(int fingers, int x, int y, int duration) {
        driver.tap(fingers, x, y, duration);
    }

    /**
     * @see {@link AppiumDriver#swipe(int, int, int, int, int)}.
     */
    public void swipe(int startx, int starty, int endx, int endy, int duration) {
        driver.swipe(startx, starty, endx, endy, duration);
    }

    /**
     * @see {@link AppiumDriver#pinch(WebElement)}.
     */
    public void pinch(WebElement el) {
        driver.pinch(el);
    }

    /**
     * @see {@link AppiumDriver#pinch(int, int)}.
     */
    public void pinch(int x, int y) {
        driver.pinch(x, y);
    }

    /**
     * @see {@link AppiumDriver#zoom(WebElement)}.
     */
    public void zoom(WebElement el) {
        driver.zoom(el);
    }

    /**
     * @see {@link AppiumDriver#zoom(int, int)}.
     */
    public void zoom(int x, int y) {
        driver.zoom(x, y);
    }

    /**
     * @see {@link AppiumDriver#getSettings()}.
     */
    public JsonObject getSettings() {
        return driver.getSettings();
    }

    /**
     * @see {@link AppiumDriver#lockScreen(int)}.
     */
    public void lockScreen(int seconds) {
        driver.lockScreen(seconds);
    }

    /**
     * @see {@link AppiumDriver#context(String)}.
     */
    public WebDriver context(String name) {
        return driver.context(name);
    }

    /**
     * @see {@link AppiumDriver#getContextHandles()}.
     */
    public Set<String> getContextHandles() {
        return driver.getContextHandles();
    }

    /**
     * @see {@link AppiumDriver#getContext()}.
     */
    public String getContext() {
        return driver.getContext();
    }

    /**
     * @see {@link AppiumDriver#rotate(ScreenOrientation)}.
     */
    public void rotate(ScreenOrientation orientation) {
        driver.rotate(orientation);
    }

    /**
     * @see {@link AppiumDriver#getOrientation()}.
     */
    public ScreenOrientation getOrientation() {
        return driver.getOrientation();
    }

    /**
     * @see {@link AppiumDriver#location()}.
     */
    public Location location() {
        return driver.location();
    }

    /**
     * @see {@link AppiumDriver#setLocation(Location)}.
     */
    public void setLocation(Location location) {
        driver.setLocation(location);
    }

    /**
     * @see {@link AppiumDriver#getAppStrings()}.
     */
    public String getAppStrings() {
        return driver.getAppStrings();
    }

    /**
     * @see {@link AppiumDriver#getAppStrings(String)}.
     */
    public String getAppStrings(String language) {
        return driver.getAppStrings(language);
    }

    /**
     * @see {@link AppiumDriver#getRemoteAddress()}.
     */
    public URL getRemoteAddress() {
        return driver.getRemoteAddress();
    }

    /**
     * @see {@link RemoteWebDriver#quit()}.
     */
    public void quit() {
        driver.quit();
    }

    /**
     * @see {@link RemoteWebDriver#getWindowHandles()}
     */
    public Set<String> getWindowHandles() {
        return driver.getWindowHandles();
    }

    /**
     * @see {@link RemoteWebDriver#switchTo()}
     */
    public TargetLocator switchTo() {
        return driver.switchTo();
    }

    /**
     * @see {@link RemoteWebDriver#getWindowHandle()}
     */
    public String getWindowHandle() {
        return driver.getWindowHandle();
    }

    /**
     * Adding support to execute JavaScript codes.
     * @param script to be executed.
     * @return the result of the execution.
     */
    public Object executeJavaScript(String script) {
        JavascriptExecutor jsExecutor = (JavascriptExecutor) driver;
        Object output = jsExecutor.executeScript(script);
        return output;
    }

}
