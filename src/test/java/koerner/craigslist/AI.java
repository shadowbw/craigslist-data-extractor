import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.StringTokenizer;
import Jama.Matrix;
import java.io.InputStream;
import java.io.InputStreamReader;

/*
 *  AI -> 	This is a Bayesian Classifier used to measure the probability of whether a string of numbers is a DFW phone number
 *  		The AI is trained on a stored set of known craigslist phone numbers in a text file at initialization. The AI contains functions
 *  		to normalize the input, which is a string of "userbody" html scraped from the craigslist post by the AdScraper. The AI then
 *  		scans across the input, and at each section of 10 numbers, it measures the probability of whether that string of numbers is either
 *  		a phone number or not a phone number. It the probability that it is a phone number is higher (exceeding a userdefined threshold
 *			of 1) than the probability that it is "not" a phone number, the AI stores it in a HashMap. The AI then continues its scans,
 *			looking for all other numbers that might be phone numbers. Once complete, it selects the phone number from the HashMap with the
 *			highest probability and returns it. If NO phone numbers were found, it returns "no phone".
 */


public class AI 
{
	/*
	 * ----------------------- Private Variables ---------------------------------------------------------------------
	 */
	private String goodListFilePath = "goodtest.txt";
	private String badListFilePath = "badtest.txt";
	private Matrix phiKGood;
	private Matrix phiKBad;
	private double probGood;
	private double probBad;
	private Set<String> goodZips;  
	
	
	/*
	 * NOTE : When I designed this, I used the name "Zip" throughout the code when I actually meant "Area" code (whoops)
	 */
	
	public AI()  // EMPTY CONSTRUCTOR
	{
		
	}
	
	/*  -------------------------------------- PUBLIC METHODS ----------------------------------------------
	 *  public void train() -> 	this method trains the classifier on the stored "good" and "bad" phone number lists
	 */
	
	public void train()
	{
		goodZips = new HashSet<String>(5000);
		BufferedReader br = null;
		ArrayList<int[]> goodList = new ArrayList<int[]>();
		ArrayList<int[]> badList = new ArrayList<int[]>();
		
		try 
		{
			InputStream configStream = getClass().getResourceAsStream("goodtest.txt");
			br = new BufferedReader(new InputStreamReader(configStream, "UTF-8"));  //reading in the txt file with the good phone numbers
			String line = "";
			while (true)
			{
				line = br.readLine();
				if (line == null) break;
				StringTokenizer st = new StringTokenizer(line); //		Tokenizer used because the phone numbers are stored in txt file
				int[] array = new int[9]; //							as "9 7 2 5 5 5 1 1 1 1". This part of the code is turning the phone
				int count = 0;//										numbers into an int[9] vector where int[0] = "972" and each 
				String zip = "";//										number after it gets its own column. So, int[1] = 5, int[2] = 5...
				while (st.hasMoreTokens())//							the number is complete at int[7], and if the area code is a valid
				{//														area code, then the last value, int[8] is set to "0". If the area code
					if (count < 2)//									of the phone# has either a 1 or 0 at the front, it cannot be an valid
					{//													area code and int[8] is assigned a value of "1". This extra value
						zip += st.nextToken();//						is a shortcut to have the classifier automatically disqualify
					}//													these phone numbers without needing much training data
					else if (count == 2)
					{
						zip += st.nextToken();
						array[count-2] = Integer.parseInt(zip);
						if (!goodZips.contains(zip))
						{
							goodZips.add(zip);
						}
						if ((zip.charAt(0) == '1') || (zip.charAt(0) == '0'))
						{
							array[8] = 1;
						}
						else
						{
							array[8] = 0;
						}
					}
					else if (count == 3)
					{
						array[count-2] = Integer.parseInt(st.nextToken());
						if ((array[1] == 0) || (array[1] == 1))
						{
							array[8] = 1;
						}
					}
					else
					{
						array[count-2] = Integer.parseInt(st.nextToken());
					}
					count++;
				}
				goodList.add(array);
			}
			br.close();
			configStream = getClass().getResourceAsStream("badtest.txt");
			br = new BufferedReader(new InputStreamReader(configStream, "UTF-8"));//				Same code as above is repeated on the "bad" list
			while (true)//															In hindsight, this whole thing should have been a 
			{//																		private method.
				line = br.readLine();
				if (line == null) break;
				StringTokenizer st = new StringTokenizer(line);
				int[] array = new int[9];
				int count = 0;
				String zip = "";
				while (st.hasMoreTokens())
				{
					if (count < 2)
					{
						zip += st.nextToken();
					}
					else if (count == 2)
					{
						zip += st.nextToken();
						array[count-2] = Integer.parseInt(zip);
						if ((zip.charAt(0) == '1') || (zip.charAt(0) == '0'))
						{
							array[8] = 1;
						}
						else
						{
							array[8] = 0;
						}
					}
					else if (count == 3)
					{
						array[count-2] = Integer.parseInt(st.nextToken());
						if ((array[1] == 0) || (array[1] == 1))
						{
							array[8] = 1;
						}
					}
					else
					{
						array[count-2] = Integer.parseInt(st.nextToken());
					}
					count++;
				}
				badList.add(array);
			}
			br.close();
		}
		catch (IOException ioe)
		{
			ioe.printStackTrace();
		}
															//				At this point, the phone numbers have been transformed into
															//				vectors and they need to be deposited into a matrix to 
		Matrix X1 = parsePhones(goodList);//								simplify the later calculations. JAMA matrix package was used.
		Matrix X0 = parsePhones(badList);
		goodList.clear(); //	<- delete the values in the no longer neeeded lists
		badList.clear();
		
		int totalTraining = X1.getRowDimension() + X0.getRowDimension(); 	// count the total amount of training data
		probGood = ((double)X1.getRowDimension())/((double)totalTraining);	// the probability that a training number is a good number
		probBad = ((double)X0.getRowDimension())/((double)totalTraining);	// the probability that a training number is a bad number
		phiKGood = parsePhi(X1);											// create a "Phi" vector for good and bad numbers
		phiKBad = parsePhi(X0);
		// Training complete. All that was necessary to get the phiKGood and the phiKBad (Phi vectors)
	}
	
	/*
	 * public synch String parseBody(String line) -> 	a public method to that AdScraper calls from AI to normalize the input before scanning
	 * 													the userbody string for phone numbers
	 */
	public synchronized String parseBody(String line)
	{
		line = line.trim();															// start by trimming the line to get rid of whitespace
		int IDStart = line.indexOf("<span class=\"postingidtext\">PostingID:");		// look for this html to indicate start of userbody
		if (IDStart >= 0)															// skip it if present
		{
			int IDend = IDStart + 49;
			String front = line.substring(0, IDStart);
			String back = line.substring(IDend);
			line = front + back;
		}
		IDStart = line.indexOf("name=\"postingID\" value=");						// an alternate html that craigslist uses
		if (IDStart >= 0)
		{
			int IDend = IDStart + 35;
			String front = line.substring(0, IDStart);
			String back = line.substring(IDend);
			line = front + back;
		}
		IDStart = line.indexOf(" pID =");											// yet another
		if (IDStart >= 0)
		{
			int IDend = IDStart + 20;
			String front = line.substring(0, IDStart);
			String back = line.substring(IDend);
			line = front + back;
		}
		line = line.replaceAll(" ", "");// 											get rid of all white space inside line and compress
		line = line.toLowerCase();//												all the characters together into one long string
		String[] spellings = {"zero", "one", "two", "three", "four", "five", "six", "seven", "eight", "nine"};
		String[] numbers = {"0", "1", "2", "3", "4", "5", "6", "7", "8", "9"};
		for (int i = 0; i < spellings.length; i++)
		{
			line = line.replaceAll(spellings[i], numbers[i]);//						replace typed numbers with actual numerical values
		}
		line = line.replaceAll("seeven", "7");//									replace some other common purposeful misspellings
		line = line.replaceAll("too", "2");//										(these are all intentional tricks by users to defeat
		line = line.replaceAll("i", "1");//											regular expressions or classifiers)
		line = line.replaceAll("o", "0");
		String parsed = "";
		for (int i = 0; i < line.length(); i++)	//									Final normalizaiton step, keep in the line ONLY
		{	//																		letters and numbers. This eliminates every other char
			char ch = line.charAt(i);				
			if ((Character.isDigit(ch) || (Character.isLetter(ch))))
			{
				parsed += ch;
			}
		}
		return parsed;
	}
	
	/*
	 * public syn String scanForPhones(String line, double threshold()) ->	Accepts input of normalized line and uses classifier to look
	 * 																		for possibles phones. Scans across the line 10 chars at a time.
	 * 																		Every set of 10 numbers is then passed to the classifier for eval.
	 * 																		If no set of 10 numbers present, return "no phone".
	 */
	
	public synchronized String scanForPhone(String line, double threshold)
	{
		boolean testBool = false;
		boolean lastCycle = false;
		Map<Double, String> results = new HashMap<Double, String>(); // <- used to store in case of multiple possible phone numbers
		String phone = "no phone";
		String front = "";
		String back = "";
		if (line.length() > 10) // <- chop string up 10 chars at a time
		{
			front = line.substring(0,10);
			back = line.substring(10);
		}
		else
		{
			front = line;
			lastCycle = true;
		}
		while (true)
		{
			boolean isAllNums = true;
			for (int i = 0; i < front.length(); i++)
			{
				if (!Character.isDigit(front.charAt(i))) // <- Check that string contains only numbers and no letters
				{
					isAllNums = false;
				}
			}
			if (isAllNums) // <- Catch point in case there are letters are present
			{
				String zip = "";
				int[] numbers = new int[9];
				for (int i = 0; i < 10; i++)//										Just like earlier in code, the numbers have to be 
				{//																	turned into the same matrix format that the Phi vectors
					if (i < 2)				//										are in so that they can be multiplied together.
					{
						zip += front.charAt(i);
					}
					else if (i == 2)
					{
						zip += front.charAt(i);
						numbers[i-2] = Integer.parseInt(zip);
						if ((zip.charAt(0) == '1') || (zip.charAt(0) == '0'))
						{
							numbers[8] = 1;
						}
						else
						{
							numbers[8] = 0;
						}
					}
					else if (i == 3)
					{
						numbers[i-2] = Character.getNumericValue(front.charAt(i));
						if ((numbers[1] == 1) || (numbers[1] == 0))
						{
							numbers[8] = 1;
						}
					}
					else
					{
						numbers[i-2] = Character.getNumericValue(front.charAt(i));
					}
				}
				ArrayList<int[]> temp = new ArrayList<int[]>();
				temp.add(numbers);
				Matrix parsedPhone = parsePhones(temp);
				Matrix goodvalue = parsedPhone.times(phiKGood); //				Finally, multiply the input vector with the Phi vector
				double left = goodvalue.get(0, 0) + Math.log(probGood);
				Matrix badvalue = parsedPhone.times(phiKBad);
				double right = badvalue.get(0, 0) + Math.log(probBad);
				if (left > right)
				{
					if (Math.abs(left - right) > threshold) //					Check against the threshold
					{
						results.put(left, front);
					}
					else if (Math.abs(left - right) > 0)
					{
						String tempzip = front.substring(0, 3);
						if (goodZips.contains(tempzip))
						{
							//System.out.println("Possibly a good phone that was missed?? - " + url + " " + front);
							testBool = true;
						}
					}
				}
			}
			if (lastCycle) break; //								<- Chop line and proceed further to next 10 characters (overlapping)
			front = front + back.charAt(0);
			front = front.substring(1);
			back = back.substring(1);
			if (back.length() == 0)
			{
				lastCycle = true;
			}
		}
		if (results.size() > 0)
		{
		Iterator<Double> it = results.keySet().iterator();
		double max = -1000000000;
		while (it.hasNext())
		{
			double value = it.next();
			if (value > max)
			{
				max = value;
			}
		}
			phone = results.get(max);
		}
		if (!phone.equals("no phone"))
		{
			String zip = phone;
			zip = zip.substring(0, 3);
			if (!goodZips.contains(zip))
			{
				//System.out.println("Unfamiliar number at line " + url + " : " + phone);
			}
		}
		if (testBool)
		{
			//System.out.println("Number that was chosen - " + phone);
		}
		return phone;
	}
	
	/*
	 * ------------------------- PRIVATE METHODS --------------------------------------------------------------------------- 
	 */
	
	/*
	 *  private Matrix parsePhi(Matrix matrix) -> 	The math needed to distill the contents of the matrix into the "Phi" vector
	 */
	private Matrix parsePhi(Matrix matrix)
	{
		double[][] sum = new double[matrix.getColumnDimension()][1]; // <-- This has to be a double[][] so that JAMA's methods can be used
		double bottom = (double)(matrix.getRowDimension() + matrix.getColumnDimension()); 
		for (int j = 0; j < matrix.getColumnDimension(); j++) //<--	Nested loops to sum each column of the matrix
		{
			double lineTotal = 0.0;
			for (int i = 0; i < matrix.getRowDimension(); i++)
			{
				lineTotal += matrix.get(i, j);
			}
			sum[j][0] = lineTotal + 1.0; // <-- plus one smoothing
			sum[j][0] = sum[j][0]/bottom;
		}
		for (int i = 0; i < sum.length; i++)
		{
			sum[i][0] = Math.log(sum[i][0]); // <-- convert to log
		}
		Matrix phi = new Matrix(sum);
		return phi;
	}
	
	/*
	 * private Matrix parsePhones(ArrayList<int[]> list) ->	This method accepts as input a list of the phone number vectors and transforms
	 * 														each individual vector into a much larger, binary-valued vector and stacks them
	 * 														all into a large matrix. Each row of the matrix contains 1071 columns. This is 
	 * 														because each number in the phone# gets mapped to a column which indicates the 
	 * 														phone number value by the position of the column in the matrix. For example,
	 * 														if the area code is "972" a "1" gets placed in the 973rd column. If the second
	 * 														number of the phone is a "2", a "1" gets placed in the 1003rd column. Once 
	 * 														finished, the method returns a matrix where nearly all columns contain zeroes,
	 * 														with ones in the columns that correspond to the individual numerical values.
	 */
	
	private Matrix parsePhones(ArrayList<int[]> list)
	{
		Matrix X = new Matrix(list.size(), 1071);
		for (int i = 0; i < list.size(); i++)
		{
			int[] array = list.get(i);
			for (int j = 0; j < array.length; j++)
			{
				if (j == 0)
				{
					X.set(i, array[j], 1.0);
				}
				else if (j == 8)
				{
					if (array[8] == 1) //  <--- the shortcut from before that notes the number to contain an invalid area code
					{
						X.set(i, 1070, 1.0);
					}
				}
				else
				{
					int index = 1000 + array[j] + (10 * (j-1)); // <- the equation used to map the number to the right column
					X.set(i, index, 1.0);
				}
			}
		}
		return X;
	}
	
	private int[] decrypt(int[] row) //	<--- NO LONGER IMPLEMENTED
	{
		int[] num = new int[10];
		int count = 0;
		for (int j = 0; j < 100; j++)
		{
			if (row[j] > 0)
			{
				num[count] = j - (count * 10);
				count++;
			}
		}
		return num;
	}
}
