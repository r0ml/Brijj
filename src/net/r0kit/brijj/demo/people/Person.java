
package net.r0kit.brijj.demo.people;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

import net.r0kit.brijj.Remotable;

public class Person implements Remotable {
  
  public Object toRemote() {
    Map<String,Object> rmt = new HashMap<String,Object>();
    rmt.put("id",id);
    rmt.put("name",name);
    rmt.put("address",address);
    rmt.put("age",age);
    rmt.put("superhero", superhero);
    return rmt;
  }
  public Person( Map<String,Object> prs) {
        this.id = getNextId();
        if (prs == null) return;
        if (prs.get("id")!= null) { 
          System.err.println("setting a person id?");
          this.id = (String)prs.get("id");
        }
        // Cast.setFields(this, prs);
        name = (String)prs.get("name");
        address = (String)prs.get("address");
        age = ((Number)prs.get("age")).intValue();
        superhero= (Boolean)prs.get("superhero"); }

  public Person(boolean withRandom) {
        if (withRandom) {
            name = RandomData.getFullName();
            address = RandomData.getAddress();
            age = RandomData.getAge();
            superhero = random.nextInt(100) == 1; }
        id = getNextId(); }

  public String id;
  public String name;
  public String address;
  public int age;
  public boolean superhero;

    @Override public String toString() { return name; }

    public static synchronized String getNextId() { return "P" + ++lastId; }
    private static int lastId = 0;
    private static final Random random = new Random(); 
}
