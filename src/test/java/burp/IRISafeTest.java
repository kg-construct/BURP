package burp;

import burp.util.Util;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class IRISafeTest {

    @Test
    public void testToIRISafe_Numeric() {
        assertEquals("42", Util.toIRISafe("42"));
    }

    @Test
    public void testToIRISafe_HelloWorld() {
        assertEquals("Hello%20World%21", Util.toIRISafe("Hello World!"));
    }

    @Test
    public void testToIRISafe_DateTime() {
        assertEquals("2011-08-23T22%3A17%3A00Z", Util.toIRISafe("2011-08-23T22:17:00Z"));
    }

    @Test
    public void testToIRISafe_SpecialChars() {
        assertEquals("~A_17.1-2_¢.€.\uD83C\uDF0D", Util.toIRISafe("~A_17.1-2_¢.€.\uD83C\uDF0D"));
    }

    @Test
    public void testToURISafe_SpecialChars() {
        assertEquals("~A_17.1-2_%C2%A2.%E2%82%AC.%F0%9F%8C%8D", Util.toURISafe("~A_17.1-2_¢.€.\uD83C\uDF0D"));
    }

    @Test
    public void testToURISafe_Zoe() {
        assertEquals("Zo%C3%AB%20Kr%C3%BCger", Util.toURISafe("Zoë Krüger"));
    }

    @Test
    public void testToURISafe_Cyrilic() {
        assertEquals("%D1%88%D0%B5%D0%BB%D0%BB%D1%8B", Util.toURISafe("шеллы"));
    }

    @Test
    public void testToIRISafe_Zoe() {
        assertEquals("Zoë%20Krüger", Util.toIRISafe("Zoë Krüger"));
    }
}
