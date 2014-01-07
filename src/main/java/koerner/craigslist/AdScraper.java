import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.SocketTimeoutException;
import java.net.URL;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

/*
 * ADSCRAPER -> runnable - a Thread spawned by the ThreadManager and assigned a copy of the AI object and a url. The AdScraper achieves the 
 * 						network connection to the assigned URL and uses Jsoup to parse the HTML. It then scrapes the Craiglist Ad Title, 
 * 						Userbody, PostingTime, and extracts the phone number (if present). It packages the data into a Row Object and returns
 * 						it to the ThreadManager. It then terminates itself. It has a number of catches in place in case there is trouble
 * 						with the network connection or the html is bad.
 */

public class AdScraper implements Runnable
{
	
	// -------------------------------- PRIVATE VARIABLES ----------------------------------------------------------------
	
	private ThreadManager pool;
	private String webpage;
	private int startNum;
	private int endNum;
	private String[] months = {"Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"};
	private int[] totalDays = {31, 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31};
	private AI ai;
	
	/*
	 * CONSTRUCTOR - accepts a url, a link to the ThreadManager object, a copy of the start and end dates, and a link to the AI object.
	 */
	
	public AdScraper(String url, ThreadManager pool, String start, String end, AI ai)
	{
		this.pool = pool;
		webpage = url;
		int startDateDay = Integer.parseInt(start.substring(4));
		int endDateDay = Integer.parseInt(end.substring(4));
		String month = start.substring(0, 3);
		int startDateMonth = findMonth(month);
		startNum = convertDateToNum(startDateMonth, startDateDay);
		month = end.substring(0, 3);
		int endDateMonth = findMonth(month);
		endNum = convertDateToNum(endDateMonth, endDateDay);
		this.ai = ai;
	}
	
	//--------------------- RUN METHOD ----------------------------------------------------------------
	
	public void run()
	{
		Document doc = null;
		String html = getHTML(webpage);  // call the getHTML method to access the webpage and copy all html into one huge String
		if (html.length() > 0)           // check to make sure the html before trying to parse with jsoup
		{
			doc = Jsoup.parse(html);
		}
		
		//Use jsoup to parse the html String
		Elements elements = doc.getElementsByClass("postinginfo");  //select the time element from the webpage
		String date = "";
		if (elements.size() > 3)
		{
			if (elements.get(3).text().contains("updated"))
			{
				date += elements.get(3).text().substring(9); //extract date and posting time
			}
			else
				date += elements.get(2).text().substring(8);
		}
		boolean confirmed = checkDate(date); // run a check to make sure the text extracted is a legitimate date
		if (confirmed)  //---------------------------------------------------------------- catch point after running the date check
		{
			//String manipulation to parse the time into a 24 hour clock
			String middle = "";
			int hour = Integer.parseInt(date.substring(date.indexOf(' ') + 1, date.indexOf(':')));
			char letter = date.charAt(date.indexOf(':') + 3);
			if ((letter == 'A') && (hour == 12))
				middle += "00";
			else if ((letter == 'P') && (hour != 12))
				middle += (hour + 12);
			else
				middle += hour;
			String front = date.substring(0, date.indexOf(' ') - 1);
			String back = date.substring(date.indexOf(':'));
			back = back.substring(0, 3);
			String finalDate = front + " " + middle + back + ":00";
			
			// scrapes the title from the html
			String title = "";
			Elements titles = doc.select("title");
			{
				for (Element titleElement : titles)
					title = titleElement.text();
			}
			
			// scrapes the userbody from the html
			Element bodyElement = doc.getElementById("postingbody");
			String body = bodyElement.toString();
			
			// counts the number of images in the ad (currently does not count PhotoBucket)
			int numImages = 0;
			if (html.indexOf("<div id=\"thumbs\"") > -1)
			{
				Element imageElement = doc.getElementById("thumbs");
				Elements images = imageElement.getElementsByTag("img");
				numImages = images.size();
			}
			
	// NEED TO ADD PHOTOBUCKET WORKAROUND -- at present the image count doesnt count photobucket links as images
			
			// use AI object to locate and extract phone number from userbody
			String sample = ai.parseBody(body);
			String phone = "no phone";
			if (sample.length() >= 10)
			{
				phone = ai.scanForPhone(sample, 0.1);
			}
			
			// Construct a Row object and deposit all data into it, then deliver to ThreadManager
			Row row = new Row(webpage, title, body, numImages, finalDate, phone);
			pool.insertRow(row);
	
		}
		
		
		pool.returnThread();  //returns thread prior to termination
		pool.threadComplete();
	}
	
	// ---------------------- PRIVATE METHODS ----------------------------------------------------------------
	
	/*
	 * convertDateToNum(month, day) -> converts numeric values of date and month to days in a year, eg: 02/20 becomes 31+20 = 51 
	 */
	
	private int convertDateToNum(int month, int day)
	{
		int total = 0;
		int stop = month - 1;
		for (int i = 0; i < totalDays.length; i++)
		{
			if (i == stop)
				break;
			else
				total += totalDays[i];
		}
		total += day;
		return total;
	}
	
	/*
	 * checkDate(date) - returns boolean -> checks the date to make sure that the PostingDate is within the start and end dates for scraping
	 * 			UPDSTE ::This method was originally designed to make sure that all Ads scraped were within start and end standards. Craigslist
	 * 			NOTE!!   recently added a new feature whereby AD posters can "re-post" each day an Ad that was originally created on a 
	 * 					 previous date. These ad "re-posts" retain the date of their original creation. This means that (with this method
	 * 					 checkDate still in place as it is now) only Ads can be scraped that were originally created within the dates of 
	 * 					 the scrape. If this scrape is run every day (or multiple times per day), then this is not an issue because all ads
	 * 					 will be scraped on their original creation dates. Removing this date check may allow all ads to be scraped even if 
	 * 					 their original creation dates fall outside the user-entered dates. 
	 */
	
	private boolean checkDate(String date)
	{
		if (date.length() < 11)
		{
			System.out.println("--------------------> invalid date format: " + webpage);
			System.out.println("This may indicate that Craigslist has altered its html structure");
			return false;
		}
		String day = date.substring(8, 10);
		int dayNum = Integer.parseInt(day);
		String month = date.substring(5, 7);
		int monthNum = Integer.parseInt(month);
		int finalNum = convertDateToNum(monthNum, dayNum);
		if ((finalNum <= startNum) && (finalNum > endNum))
			return true;
		else
		{
			return false;
		}
	}
	
	/*
	 * findMonth(month) -> a simple method converting numeric months into Strings "Jan" "Feb" etc.
	 */
	
	private int findMonth(String month)
	{
		for (int i = 0; i < months.length; i++)
		{
			if (month.equals(months[i]))
				return i + 1;
		}
		return -1;
	}
	
	/*
	 * getHTMLT(page) --> This method used to establish a connection to the url and scrape the html from it into a String to be parsed
	 * 					  by Jsoup. This method contains special Exception handling to try and account for poor network connections, 
	 * 					  as a result for Craigslist throttling. Connections have been assigned lengthy timeouts, and a re-try counter. 
	 * 					  This method will attempt "max" times to achieve a connection. 
	 */
	
	private String getHTML(String page)
	{
		int timeout = 25000;
		int max = 10;
		boolean connected = false;
		String html = "";
		URL url = null;
		HttpURLConnection conn = null;
		int count = 1;
		BufferedReader br = null;
		InputStream input = null;
		while ((!connected) && (count < max))
		{
			try
			{
				connected = true;
				url = new URL(page);
				conn = (HttpURLConnection)url.openConnection();
				conn.setConnectTimeout(timeout);
				conn.setReadTimeout(timeout);
				conn.connect();
				input = conn.getInputStream();
			}
			catch (SocketTimeoutException ste)
			{
				connected = false;
				System.out.println("Connection timeout in AdScraper. Craigslist is probably throttling your connection.");
			}
			catch (MalformedURLException mue)
			{
				connected = false;
				System.out.println("Malformed URL Exception in Indexer");
				break;
			}
			catch (IOException ioe)
			{
				System.out.println("Craigslist is likely throttling your internet connection to slow down the scrape.");
				connected = false;
			}
			count++;
			if (!connected)
			{
				System.out.println("Connection failed -> " + count);
				System.out.println("Retrying Connection");
			}
		}
		if (connected)  //---------------------------------------------------------------- catch point if connection not established
		{
			String line = "";
			try
			{
				br = new BufferedReader(new InputStreamReader(input));
				while (true)
				{
					line = br.readLine();
					if (line == null) break;
					html += line;
				}
				br.close();
			}
			catch (IOException ioe)
			{
				pool.returnThread();
			}
			
		}
		
		return html;
	}
	
	
}
