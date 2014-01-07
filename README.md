### Craigslist-Data-Extractor

This is a crawler/scraper program which scans craigslist ads and extracts data from each ad. The crawler is multi-threaded in order to speed up performance. The extracted data is packaged into an xml file which is created in the same directory where the script is run. The xml file is named with "craigslist_{timestamp}" to prevent duplication. 

#### Data Extracted

- The ad url
- The ad Title
- The user's ad text
- Number of images posted via craigslist
- Initial post time
- Phone number (if present)

#### Phone Number Extraction

The phone number is extracted using a Baysian Classifier. The classifier is trained on two phone number files located in the /resource directory. The "goodtest.txt" file contains a sample of valid Dallas/FortWorth phone numbers. The "badtest.txt" file contains a sample of randomly created phone number sequences, combined with invalid phone number combinations. To customize this classifier to work in different geographical craigslist regions, the "goodtest.txt" file needs to be replaced with a substantial list of sampled phone numbers from the target region. The sample should be large, approxiamtely 1500 phone numbers. The phone numbers need to be placed in the "goodtest.txt" file, one number per line, each digit sepeareted by a " " space. eg -> 2 1 4 2 3 4 5 6 7 8

#### Running the Program

The program can be installed using the maven command "mvn package appassembler:assemble" which will packaged the program into an executable jar file, which can be executed using one of the scripts created in the /target/appassembler/bin directory. Upon starting the program, the terminal prompts the user to select the dates for the scraper to search within. To search only a single day, enter the same starting and ending date. 

#### Speed of Execution

Craigslist is exceptionally hostile to scraper programs. And while this program is multi-threaded, Craigslist still throttles the number of the connections allowed heavily. Depending on the connection behind which the program is executed, the speed of execution will vary drastically. If craigslist is able to throttle the connection, the execution will progress much more slowly. A VPN may prevent craigslist from being able to throttle the connection.

A second factor which slows execution is the number of days that the scrape is targeted over and the number of categories scraped. Each category varies widely in the number of average posts per day. Even with only a few categories, the number of ads per day is easily several thousand. Each ad requires a connection, and if craigslist suceeds in throttling the connections, the speed of execution becomes exponentially slower. It is highly recommeneded that a small window is used while being throttled.  

#### Output

The data is outputted to an xml file that is created in the same directory as the program's execution. To avoid conflicting with other files, each xml file is timestamped. The xml rootnode is ADS, which contains a list of AD. Each AD contains an attribute "url" which is the specific ad's original url. AD nodes contains children nodes: TITLE, USERBODY, IMAGES, POSTING_TIME, and PHONE. If no phone was identified in the ad, the PHONE node contains "no phone" as text. Each AD has a url attribute and contains all of the aforementioned children nodes with at least some sort of text entry. The xml was organized so that it could be easily converted to a relational table.

#### Customization

There are a few parameters that can be altered in the code. 
* The SpiderPrimary.java file contains a String array "categories" which is the list of craigslist category urls to scrape. Adding urls to this list or removing them, allows the user to customize the categories to scrape.
* The ThreadManager.java contains a private variable "MAX_THREADS" which caps the number of connections active at once. Increasing or decreasing the number may improve or worsen performance depending on whether craigslist has throttled the connection.
* As stated above, the classifier training file "goodtest.txt" can be changed to alter the geographic location to be scraped. A large sample of phone numbers from the targeted location are needed to train the classifier for the region.
