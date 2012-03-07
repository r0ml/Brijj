package net.r0kit.brijj.demo.people;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import net.r0kit.brijj.Brijj;
import net.r0kit.brijj.RemoteRequestProxy;

public class People extends RemoteRequestProxy {

  public static Map<String, Person> smallCrowd = createCrowd(10);
  public static Map<String, Person> largeCrowd = createCrowd(500);

  public People(HttpServletRequest q, HttpServletResponse s) {
    super(q, s);
  }

  /** We maintain 2 lists of people, small (~10 people) and large (~1000). The
   * smaller is for when we want to show them all on the screen at the same
   * time, the larger for when we don'e. */
  @Brijj.RemoteMethod public Collection<Person> getSmallCrowd() {
     synchronized(smallCrowd) { return smallCrowd.values(); }
  }

  /** Insert a person into the set of people
   * 
   * @param person The person to add or update */
  @Brijj.RemoteMethod public String setPerson(Person person) {
    synchronized(smallCrowd) { smallCrowd.put(person.id, person); }
    return "Updated values for " + person;
  }

  /** Delete a person from the set of people
   * 
   * @param id The id of the person to delete */
  @Brijj.RemoteMethod public String deletePerson(String id) {
    Person person = null; 
    synchronized(smallCrowd) { person = smallCrowd.remove(id); }
    return person == null ? "Person does not exist" : "Deleted " + person;
  }

  @Brijj.RemoteMethod public List<Person> getMatchingFromLargeCrowd(String filter) {
    List<Person> reply = new ArrayList<Person>();
    Pattern regex = Pattern.compile(filter, Pattern.CASE_INSENSITIVE);
    for (Person person : largeCrowd.values()) {
      if (regex.matcher(person.name).find()) {
        reply.add(person);
      }
    }
    return reply;
  }

  public static Map<String, Person> createCrowd(int count) {
    Map<String, Person> reply = new ConcurrentHashMap<String, Person>();
    for (int i = 0; i < count; i++) {
      Person person = new Person(true);
      reply.put(person.id, person);
    }
    return reply;
  }
}
