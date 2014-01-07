
/*CRAIGSLIST SPIDER - (default) A multi-threaded scraper that scans craigslist for posts in between a user-entered start and cutoff date. The scraper
 *  				scans the index pages withint the Electronics, Car Parts, Cell Phones, and Computer categories and accesses each 
 *  				individual ad. The scraper deposits the Title, Userbody, PostingDateTime, and Phone number (if present) into an xml 
 *  				file. The Phone number is extracted from the Userbody via a Naive Bayes classifier trained on a sample of DFW
 *  				Craigslist phone numbers. 
 *  
 *  				The Spider consists of 6 Objects:
 *  					1) Spider Primary (this file) -> This contains the main method. It instantiates the AI Class (Classifier) and 
 *  													ThreadPoolManager Class (runnable) and starts the thread. This class generally 
 *  													functions as the manager class for the entire program.
 *  
 *  					2) AI -						 -> The AI is a Naive Bayes Classifier which is trained and then passed to each AdScraper 
 *  													which uses it to extract the phone number the Userbody.
 *  					
 *  					3) Indexer - 				 -> The Indexer is created and given a category URL from craiglist. It then scans 
 *  													that category backwards from the most recent craigslist ads posted until it finds 
 *  													the user-entered start date. It then scrapres the urls for each Ad link going backwards
 *  													until it reaches the user-entered stop date. Once it finishes scanning, it loads
 *  													the urls into the ThreadManager's Queue.
 *  
 *  					4) ThreadManager -			 ->	The ThreadManager maintains the xml file and is outfitted with a number of 
 *  													synchronized methods. The ThreadManager has a thread count limit, which it maintains.
 *  													The ThreadManager's primary purpose is to receive url's into its Queue, and then spawn
 *  													a limited number of AdScraper objects for each url as it empties its Queue. It waits
 *  													for each AdScraper to retrieve the html and package it into a Row Object, which each
 *  													AdScraper then returns to the ThreadManager. Upon receiving the Row Object, the 
 *  													ThreadManager unpacks it and formulates the data into nodes for the xml file. The
 *  													ThreadManager keeps track of all Threads it has created, and when all Threads have
 *  													notified of completion, the ThreadManager terminates the program.
 *  
 *  					5) AdScraper -				 -> The AdScrper is a runnable thread that is spawned by the TheadManager. It is given
 *  													a URL and the AI object. It utilizeds Jsoup to parse the html from the url. It
 *  													extracts specific craigslist post information and checks to ensure it isnt' copying
 *  													a "repost" from a previous date. The AdScraper packages the datat into a Row Object 
 *  													and returns it to the ThreadManager. The AdScraper then terminates.
 *  
 *  					6) Row - 					 -> Just a capsule object to simplify the transfer of data from the AdScraper to the 
 *  													ThreadManager. Contains only getters and setters. 
 * 
 */

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Calendar;
import java.util.Queue;



public class SpiderPrimary 
{
	private static String[] months = {"Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"};
	private static int year = 0;
	
	public static void main(String[] args)
	{
		/*
		 * -------------------------- Main Method -----------------------
		 */
		
		// String Array containing URLs for Craigslist product categories to be scanned  <------------------------------------------------
		String[] categories = {"http://dallas.craigslist.org/ela/index.html", // <------------- ADD OR REMOVE URLS HERE
							   "http://dallas.craigslist.org/pta/index.html", // <------------- TO EXPAND THE SCRAPER INTO OTHER 
							   "http://dallas.craigslist.org/moa/index.html", // <-------------  CATEGORIES
							   "http://dallas.craigslist.org/sya/index.html"};  // <------------------------------------------------------
		
		// Some code to ask the User to input the starting and ending dates. Check the user input to verify that it's valid
		year = Calendar.getInstance().get(Calendar.YEAR);
		boolean monthValid = false;
		boolean dayValid = false;
		String endDate = "";
		String startDate = "";
		System.out.println("WARNING: The length of time required to complete this scrape depends on two things:");
		System.out.println("1: The total number of days you want to scrape across");
		System.out.println("2: Whether Craigslist is able to throttle your internet connection");
		String endMonth = "";
		String endDay = "";
		String startMonth = "";
		String startDay = "";
		System.out.println("-------------------------------------------------------");
		System.out.println("Starting date for the Craigslist scrape:");
		while (!monthValid)
		{
			monthMessage();
			endMonth = getResponse();
			monthValid = checkMonth(endMonth);
		}
		while (!dayValid)
		{
			System.out.println("Please enter the day portion of the starting date.");
			endDay = getResponse();
			dayValid = checkDay(endDay, endMonth);
		}
		if (endDay.length() < 2)
			endDay = "0" + endDay;
		endDate += months[Integer.parseInt(endMonth) - 1] + " " + endDay;
		System.out.println("Start the scrape on " + endDate + ". Is this correct? Please answer YES or NO.");
		if (!confirmEntry())
		{
			System.out.println("Either this isn't the date you wanted, or you failed to confirm it. Exiting program, please try it again.");
			System.exit(0);
		}
		endDate = getYesterday(endMonth, endDay);
		monthValid = false;
		dayValid = false;
		System.out.println("Ending date for the Craigslist scrape:");
		while (!monthValid)
		{
			monthMessage();
			startMonth = getResponse();
			monthValid = checkMonth(startMonth);
		}
		while (!dayValid)
		{
			System.out.println("Please enter the day portion of the ending date.");
			startDay = getResponse();
			dayValid = checkDay(startDay, startMonth);
		}
		if (startDay.length() < 2)
			startDay = "0" + startDay;
		startDate += months[Integer.parseInt(startMonth) - 1] + " " + startDay;
		System.out.println("End the scrape at " + startDate + ". Is this correct? Please answer YES or NO.");
		if (!confirmEntry())
		{
			System.out.println("Either this isn't the date you wanted, or you failed to confirm it. Exiting program, please try it again.");
			System.exit(0);
		}
		
		//Instantiate and train AI object
		AI ai = new AI();
		ai.train();
		
		// Instantiate ThreadPoolManager and start the thread.
		ThreadManager pool = new ThreadManager(startDate, endDate, ai);
		Thread poolThread = new Thread(pool);
		poolThread.start();
		
		// For loop to being looping through the Craigslist product categories and creating and Indexer object to scan the categories and
		// gather the individual ad pages. Once the indexer gather's the URLs, it sends the pages to the ThreadPoolManager via synchronized
		// methods to deposit the ad urls into a single communal queue to control the number of network connections established at once
		for (int i = 0; i < categories.length; i++)
		{
			Indexer indexer = new Indexer(categories[i], startDate, endDate); /// Indexer object created and handed a category to scan
			Queue<String> q = indexer.createIndex();  // Indexer scans category and places all ad urls into a single queue
			pool.addToQ(q); // that queue is then passed to the ThreadPool which unpacks them and puts them in one large queue
		}
		pool.setFinished(); //notify the ThreadPool when the four loop completes
	}
	
	//------------------------------- PRIVATE METHODS -----------------
	
	
	/*
	 * getResponse() - returns a String. This is just the code needed to read a user-entry from the console
	 */
	private static String getResponse()
	{
		String date = "";
		BufferedReader br = null;
		try {
			br = new BufferedReader(new InputStreamReader(System.in));
			date = br.readLine();
		} catch (IOException ioe) {};
		return date;
	}
	
	/*
	 * Just prints a message to terminal
	 */
	
	private static void monthMessage()
	{
		System.out.println("Please enter the number that corresponds to the MONTH:");
		System.out.println("1) January\n2) February\n3) March\n4) April\n5) May\n6) June\n7) July\n8) August\n9) September\n10) October\n11) November\n12) December");
		System.out.println("-------------------------------------------------------");
	}
	
	/*
	 * Checks to make sure that the month entered is correct.
	 */
	private static boolean checkMonth(String month)
	{
		if (!month.matches("[0-9]+"))
			return false;
		int num = Integer.parseInt(month);
		if ((num < 1) || (num > 12))
			return false;
		return true;
	}
	
	/*
	 * Checks to make sure that the Day entered is correct. Also accounts for leap years.
	 */
	
	private static boolean checkDay(String day, String month)
	{
		if (!day.matches("[0-9]+"))
			return false;
		int num = Integer.parseInt(day);
		if ((num < 1) || (num > 31))
			return false;
		int monthNum = Integer.parseInt(month);
		if ((monthNum == 4) || (monthNum == 6) || (monthNum == 9) || (monthNum == 11))
		{
			if (num > 30)
				return false;
		}
		if (monthNum == 2)
		{
			if (((year-2012) % 4 == 0) && (num > 29)) //account for leap year
				return false;
			else if (num > 28)
				return false;
		}
		return true;
	}
	
	/*
	 * Just forces the user to accept their entry or signals the program to terminate and start over.
	 */
	
	private static boolean confirmEntry()
	{
		String response = getResponse();
		response = response.toLowerCase();
		if (response.charAt(0) == 'y')
			return true;
		else
			return false;
	}
	
	/*
	 * Because the Craigslist scraper actually works its way backwards through the Craigslist index pages, the user's entry
	 * needs to be modified. Therefore, the scraper needs to subtract one day from the user's entry.
	 */
	
	private static String getYesterday(String endMonth, String endDay)
	{
		int day = Integer.parseInt(endDay);
		int month = Integer.parseInt(endMonth);
		System.out.println(endMonth);
		if (day > 1)
			day -= 1;
		else
		{
			month -= 1;
			if (month == 0)
				month = 12;
			if ((month==4) || (month==6) || (month==9) || (month==11))
				day = 30;
			else if (month==2)
			{
				if ((year-2012) % 4 == 0)
					day = 29;
				else
					day = 28;
			}
			else
				day = 31;
		}
		String strDay = "" + day;
		if (day < 10)
			strDay = "0" + day;
		String endDate = months[month-1] + " " + strDay;
		return endDate;
	}
}

