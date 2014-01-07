
import junit.framework.TestCase;

public class AITest extends TestCase
{
	
	
	public AITest(String name)
	{
		super(name);
	}
	
	public void testAI() throws Exception
	{
		AI ai = new AI();
		ai.train();
		String[] tests = {"want to sell this cell phone for really cheap cause I just robbed a guy hit me up on email", 
				"1299435743-90345389719024983jklskdiwi18983kjkld---1--1-11=-2-3..4/3210923",
				"Call me to buy this stuff <br> <br> two one four 98I. three seeven 2 1",
				"972935791274901991232148872309489290342984691823982490903894322",
				"<> <html>1287ksjdfkjawer10238490890u8989987676725623fio88kjkfsdyu2u490898389023yu456nmncjkxuyu121e8342834986092</html>"};
		String[] answers = {"no phone", "9034538971", "2149813721", "2148872309", "no phone"};
		for (int i = 0; i < 5; i++)
		{
			String result = ai.scanForPhone(ai.parseBody(tests[i]), 1.0);
			assertEquals(result, answers[i]);
		}
	}
}


