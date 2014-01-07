
public class Main 
{
	public static void main(String[] args)
	{
		AI ai = new AI();
		ai.train();
		String test1 = "want to sell this cell phone for really cheap cause I just robbed a guy hit me up on email";
		String test2 = "1299435743-90345389719024983jklskdiwi18983kjkld---1--1-11=-2-3..4/3210923";
		String test3 = "Call me to buy this stuff <br> <br> two one four 98I. three seeven 2 1";
		String test4 = "972935791274901991232148872309489290342984691823982490903894322";
		String test5 = "<><> <html>1287ksjdfkjawer10238490890u8989987676725623fio88kjkfsdyu2u490898389023yu456nmncjkxuyu121e8342834986092</html>";
		System.out.println(ai.scanForPhone(ai.parseBody(test1), 1.0));
		System.out.println(ai.scanForPhone(ai.parseBody(test2), 1.0));
		System.out.println(ai.scanForPhone(ai.parseBody(test3), 1.0));
		System.out.println(ai.scanForPhone(ai.parseBody(test4), 1.0));
		System.out.println(ai.scanForPhone(ai.parseBody(test5), 1.0));
	}
}
