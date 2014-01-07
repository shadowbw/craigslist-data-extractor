
/*
 *  Row object -> nothing but a data storage capsule
 */

public class Row 
{
	private final String title;
	private final String body;
	private final int numImages;
	private final String url;
	private final String postingTime;
	private final String phone;
	
	/*
	 * PUBLIC METHODS, no private methods - contains only getter methods to extract data
	 */
	
	public Row(String url, String title, String body, int numImages, String postingTime, String phone)
	{
		this.url = url;
		this.title = title;
		this.body = body;
		this.numImages = numImages;
		this.postingTime = postingTime;
		this.phone = phone;
	}
	
	public String getUrl()
	{
		return url;
	}
	
	public String getTitle()
	{
		return title;
	}
	
	public String getBody()
	{
		return body;
	}
	
	public int getImages()
	{
		return numImages;
	}
	
	public String getPostingTime()
	{
		return postingTime;
	}
	
	public String getPhone()
	{
		return phone;
	}
}
