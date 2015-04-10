package ru.sbt.qa.allure.json;

public class TestCase {
	public String uid;
	public String title;
	public String name;
	public TimeStats time;
	public String severity,status;
	class TimeStats {
		Long start=0L,stop=0L,duration=0L;
	}
}
