import java.io.File;
import java.util.Date;
import java.util.LinkedList;
import java.util.Queue;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.w3c.dom.Document;
import org.w3c.dom.Element;

/*
 * 	ThreadManager - implements Runnable
 * 					Since this is a multi-threaded scraper designed to scrape thousands of craigslist ads as quickly as possible, the
 * 					ThreadManager handeles the connections and limits the number that can be active at once. The ThreadManager is the most 
 * 					active class that functions as a hub for all program activity. It spawns the AdScraper objects, giving each one a url
 * 					to go scrape. The AdScrapers return with the desired information in the form of a Row object, which is just a capsule
 * 					that contains all data needed for the xml database. The thread manager maintains the write connection and unpacks the Row
 * 					objects so that they can be deposited into SQL. Nearly all methods are synchronized, and (if necessary) the 
 * 					ThreadManager idles while it waits for the AdScrapers to call its methods. When the queue of urls to scrape is empty,
 * 					and all AdScrapers have returned, the ThreadManager closes the write connections and terminates the program.
 */


public class ThreadManager implements Runnable
{
	
	// -----------------------PRIVATE VARIABLES -------------------------------------------------
	
	private Queue<String> q;
	private int MAX_THREADS;
	private int THREADS_OUT;
	private int totalThreads;
	private int threadsBack;
	private boolean indexerFinished;
	private String startDate;
	private String endDate;
	private AI ai;
	private long startTime;
	private long endTime;
	private Document doc = null;
	private Element rootElement = null;
	
	// -------------- CONSTRUCTOR ----------------------------------------------------------------
	
	public ThreadManager(String start, String end, AI ai)
	{
		startTime = System.currentTimeMillis();
		q = new LinkedList<String>(); // the communal queue (LinkedList)
		MAX_THREADS = 11; // Max number of connections allowed active at once. Increase this for faster performance if network can handle it
		THREADS_OUT = 0;  // keeps track of AdScraper threads dispatched
		threadsBack = 0;  // keeps track fo AdScraper threads that completed
		totalThreads = 0; 
		indexerFinished = false;  // notification from primary that indexer for loop is completed
		startDate = start;
		endDate = end;
		this.ai = ai;  // AI object to be passed to AdScrapers
		try // XML document constructed
		{
			DocumentBuilderFactory docFactory = DocumentBuilderFactory.newInstance();
			DocumentBuilder docBuilder = docFactory.newDocumentBuilder();
			doc = docBuilder.newDocument();
			rootElement = doc.createElement("ads");
			doc.appendChild(rootElement);
		}
		catch (Exception e) {};
	}
	
	//--------------------------- RUN METHOD --------------------------------
	
	public void run()
	{
		waitForQ();  // initial wait until Q starts notifying ready
		while ((!allThreadsBack()) || (!indexFinished()))  // Execute while loop until both conditions are met
		{												   // When all threads notify complete and indexer is complete, loop terminates	
			if (q.isEmpty())   //wait in case the AdScrapers hit network connection trouble and the Q runs empty
			{
				pauseForQ();
			}
			else
			{
				waitForThread();
				String url = removeFromQ(); // unload a url from the queue and assign it to an AdScraper thread
				assignThread(url);
			}
			System.out.println("Waiting on: " + (totalThreads - threadsBack) + " ad scrapes."); // print to console indicating progress towards completion
		}
		try
		{
			TransformerFactory transformerFactory = TransformerFactory.newInstance();
			Transformer transformer = transformerFactory.newTransformer();
			DOMSource source = new DOMSource(doc);
			Date date = new Date();
			String timestamp = "" + date.getTime();
			StreamResult result = new StreamResult(new File("craigslist_" + timestamp + ".xml"));
			transformer.transform(source, result);
		}
		catch (Exception e) {};
		
		endTime = System.currentTimeMillis();
		System.out.println("Total elapsed runtime = ");  // print out total run time of program
		System.out.println(endTime - startTime);
	}
	
	//---------------------------------- PUBLIC METHODS --------------------------------
	
	
	/*
	 * insertRow(Row row) - synchronized void - accepts Row object and unpacks it to construct a SQL statements for insertion 
	 */
	
	public synchronized void insertRow(Row row)
	{
		String url = row.getUrl();   /// call get Methods from Row to extract data from it
		String title = row.getTitle();
		String body = row.getBody();
		int images = row.getImages();
		String posting_time = row.getPostingTime();
		String phone = row.getPhone();
		try
		{
			Element ad = doc.createElement("ad");
			rootElement.appendChild(ad);
			ad.setAttribute("url", url);
			Element adTitle = doc.createElement("title");
			adTitle.appendChild(doc.createTextNode(title));
			ad.appendChild(adTitle);
			Element adBody = doc.createElement("userbody");
			adBody.appendChild(doc.createTextNode(body));
			ad.appendChild(adBody);
			Element adNumImages = doc.createElement("numImages");
			adNumImages.appendChild(doc.createTextNode("" + images));
			ad.appendChild(adNumImages);
			Element adPostingTime = doc.createElement("adPostingTime");
			adPostingTime.appendChild(doc.createTextNode(posting_time));
			ad.appendChild(adPostingTime);
			Element adPhone = doc.createElement("phone");
			adPhone.appendChild(doc.createTextNode(phone));
			ad.appendChild(adPhone);
		} catch (Exception e) {};
	}
	
	/*
	 *  addTOQ(Queue<String>) - synchronized void - accepts a Queue of Strings from the Indexer object and unloads it, re-filing the urls
	 *  											into the ThreadManager's own private Queue. NotifiesAll to once Queue is full to start
	 *  											creating and dispatch AdScraper threads. Increments totalThreads variable to keep count
	 *  											of how many urls are being scraped.
	 */
	
	public synchronized void addToQ(Queue<String> queue)
	{
		while (!queue.isEmpty())
		{
			String link = queue.remove();
			q.add(link);
			totalThreads++;
		}
		notifyAll();
	}
	
	/*
	 * returnThread() - synchronized void - When an AdScraper object completes and returns a row object, it calls this method to "free up"
	 * 										the thread it was using. The ThreadManager then dispatches a new AdScraper. This is used for 
	 * 										thread control based on the MAX_THREADS variable.
	 */
	
	public synchronized void returnThread()
	{
		THREADS_OUT--;
		notifyAll();
	}
	
	/*
	 * setFinished() - synchronized void - allows the Indexer to notify the ThreadManager that it is finished scanning for Ads.
	 */
	
	public synchronized void setFinished()
	{
		indexerFinished = true;
	}
	
	/*
	 *  threadComplete() - synchronized void - allows the AdScraper to notify the ThreadManager that it has terminated its thread.
	 */
	
	public synchronized void threadComplete()
	{
		threadsBack++;
		notifyAll();
	}
	
	//----------------------  PRIVATE METHODS -----------------------------------
	
	/*
	 * waitForQ() - synchronized void - method to wait for the ThreadManager's Queue object to be filled by the indexer. ThreadManager
	 * 									starts spawning AdScraper after it wakes from this method.
	 */
	
	private synchronized void waitForQ()
	{
		while (q.isEmpty())
		{
			try {
				wait();
			} catch (InterruptedException e) {}
		}
	}
	
	/*
	 * indexFinishd() - synchronized void - a synchronized method to allow the ThreadManager to check if the indexer is finished
	 */
	
	private synchronized boolean indexFinished()
	{
		return indexerFinished;
	}
	
	/*
	 * pauseForQ() - synchronized void - if the indexer is slow (for some reason) and the ThreadManager should manage to empty the Queue, the
	 * 									ThreadManager will periodically check to see if the Q is filled again. This method really should never
	 * 									happen.
	 */
	
	private void pauseForQ()
	{
		try
		{
			Thread.sleep(5000);
		} catch (InterruptedException ie) {}
	}
	
	/*
	 * assignThread(String url) - the ThreadManager assign a url from the Queues to an AdScraper and starts its thread
	 */
	
	private synchronized void assignThread(String url)
	{
		AdScraper adScraper = new AdScraper(url, this, startDate, endDate, ai);
		Thread thread = new Thread(adScraper);
		THREADS_OUT++;  
		thread.start();
	}
	
	/*
	 * removeFromQ() - removes a url from the top of the Queue
	 */
	
	private synchronized String removeFromQ()
	{
		String url = q.remove();
		return url;
	}
	
	/*
	 * waitForThread() - causes the ThreadManager to wait while the max number of threads are spawned. The ThreadManager will wait until
	 * 					and AdScraper calls the returnThread() method. 
	 */
	
	private synchronized void waitForThread()
	{
		while (THREADS_OUT == MAX_THREADS)
		{
			try {
				wait();
			} catch (InterruptedException e) {}
		}
	}
	
	/*
	 * NOT IMPLEMETNED
	 */
	
	private synchronized boolean isEmpty()
	{
		if (q.isEmpty())
			return true;
		else
			return false;
	}
	
	/*
	 * allThreadsBack() - checks to see whether all threads have returned. If so, the while loop can break, and terminate the program.
	 */
	
	private synchronized boolean allThreadsBack()
	{
		if (threadsBack == totalThreads)
			return true;
		else
			return false;
	}
}
