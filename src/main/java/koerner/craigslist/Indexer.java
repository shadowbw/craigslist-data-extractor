import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.util.LinkedList;
import java.util.Queue;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

/*
 * Indexer -> Accepts the start and end dates and scans the assigned category page scraping the url for each individual craigslist ad.
 * 			  Once it has finished scraping the individual craigslist ads, it sends them to the ThreadManager. 
 */

public class Indexer 
{
	
	// -------------------PRIVATE VARIABLES ----------------------------------------------------------------
	private String rootURL;
	private String startDate;
	private String endDate;
	
	//---------------------- CONSTRUCTOR ------------------------------------------------------------------
	
	public Indexer(String categoryURL, String start, String end)
	{
		this.rootURL = categoryURL;
		this.startDate = start;
		this.endDate = end;
	}
	
	// ---------------------- PUBLIC METHODS ----------------------------------------------------------------
	
	
	/*
	 *  createIndex() - returns Queue<Strng> -> cycles through Craigslist page structure locating start and end times and scraping
	 *  			    individual Ad urls along the way. Once scraping is complete, sends Queue to the ThreadManager 
	 */
	
	public Queue<String> createIndex()
	{
		Queue<String> q = new LinkedList<String>();
		String html = "";
		Document doc = null;
		int pageCount = 0;
		String url = rootURL;
		boolean startFound = false;
		boolean endFound = false;
		while (!startFound)       /// while loop to cycle backwards until start date is located in the webpage banner
		{
			if (pageCount > 0)  // some String manipulation to cycles through the craigslist Ad index history
			{
				int lastperiod = rootURL.lastIndexOf(".");
				String front = rootURL.substring(0, lastperiod);
				String back = "00.html";
				url = front + pageCount + back;
			}
			System.out.println("Scanning Craigslist index for the start date");
			html = getHTML(url);                     // uses getHTML to establish the network connection
			if (html.length() > 0)
			{
				doc = Jsoup.parse(html);
				Elements elements = doc.getElementsByClass("ban"); // searches for banner elements which indicate start and end of days
				for (Element element : elements)
				{
					String date = element.text();
					int found = date.indexOf(startDate);
					if (found > -1)
					{
						startFound = true;	
						System.out.println("Start Date Located");
					}
				}
			}
			else
			{
				System.out.println("Indexer html had 0 length");
			}
			if (!startFound)
				pageCount++;
		}
		while(!endFound)                     /// while loop that continues backwards cycle until ending banner is located
		{									 /// for each day that the end isnt found, scrape Ad pages
			Elements elements = doc.getElementsByClass("row");
			for (Element element : elements)
			{
				Elements links = element.getElementsByAttribute("href");
				String hyperlink = links.get(1).toString();
				int start = hyperlink.indexOf('"') + 1;
				int end = hyperlink.indexOf('"', start);
				hyperlink = hyperlink.substring(start, end);
				hyperlink = "http://dallas.craigslist.org" + hyperlink;
				q.add(hyperlink);
			}
			Elements dates = doc.getElementsByClass("ban");
			for (Element date : dates)
			{
				String text = date.text();
				int found = text.indexOf(endDate);
				if (found > -1)
					endFound = true;
			}
			if (!endFound)          // the end is found but still need the ads remaining on the page before the end banner, one last scrape
			{
				pageCount++;
				if ((pageCount > 0) && (!endFound))
				{
					int lastperiod = rootURL.lastIndexOf(".");
					String front = rootURL.substring(0, lastperiod);
					String back = "00.html";
					url = front + pageCount + back;
				}
				html = getHTML(url);
				doc = Jsoup.parse(html);
			}
		}
		return q;
	}
	
	/*
	 *  getHTML() -> See comments for this same method in AdScraper Object for information
	 */
	
	private String getHTML(String webpage)
	{
		int timeout = 25000;
		int max = 10;
		boolean connected = false;
		String html = "";
		URL url = null;
		HttpURLConnection conn = null;
		int responseCode = 0;
		int count = 1;
		BufferedReader br = null;
		InputStream input = null;
		while ((!connected) && (count < max))
		{
			try
			{
				connected = true;
				System.out.println("Scanning index page: " + webpage);
				url = new URL(webpage);
				conn = (HttpURLConnection)url.openConnection();
				responseCode = conn.getResponseCode();
				conn.setConnectTimeout(timeout);
				conn.setReadTimeout(timeout);
				conn.connect();
				input = conn.getInputStream();
			}
			catch (SocketTimeoutException ste)
			{
				connected = false;
				System.out.println("SocketTimeout in Indexer");
			}
			catch (MalformedURLException mue)
			{
				connected = false;
				System.out.println("Malformed URL Exception in Indexer");
				break;
			}
			catch (IOException ioe)
			{
				System.out.println("Craigslist may be throttling your connection. This will slow the scrape.");
				connected = false;
			}
			count++;
			if (!connected)
			{
				System.out.println("Connection failed -> " + count);
				System.out.println("Retrying the page that timed out");
			}
		}
		if (connected)
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
				System.out.println("BufferedReader failed in Indexer");
			}
		}
		
		return html;
	}
}
