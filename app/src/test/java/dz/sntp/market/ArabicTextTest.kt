package dz.sntp.market

import dz.sntp.market.util.ArabicText
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The shop search runs over names in Arabic, typed on an Arabic keyboard where
 * the same word has several accepted spellings. Every case below is a real pair
 * a trader would type: if any of them fails, a correct search over a seven-name
 * directory reports "no match".
 */
class ArabicTextTest {

    @Test
    fun `hamza carriers fold to bare alef`() {
        assertTrue(ArabicText.matches("بيع ملابس بالجملة عند السعيد", "السعيد"))
        assertTrue(ArabicText.matches("بيع ملابس بالجملة عند السعيد", "السعيد"))
        assertTrue(ArabicText.matches("أحمد", "احمد"))
    }

    @Test
    fun `alef maqsura and ya are the same letter`() {
        // The seed spells it «شلبي»; a keyboard may produce «شلبى».
        assertTrue(ArabicText.matches("شلبي للتسوق", "شلبى"))
        assertTrue(ArabicText.matches("شلبي للتسوق", "شلبي"))
    }

    @Test
    fun `ta marbuta and ha are the same letter`() {
        assertTrue(ArabicText.matches("محلات SNTP للملابس الرجالية", "محله"))
    }

    @Test
    fun `tatweel and harakat are ignored`() {
        assertTrue(ArabicText.matches("مـــلابس", "ملابس"))
        assertTrue(ArabicText.matches("مَلابِس", "ملابس"))
    }

    @Test
    fun `latin case and extra spaces do not break the search`() {
        assertTrue(ArabicText.matches("SK 13 SNTP", "sk 13"))
        assertTrue(ArabicText.matches("Market Dz KK", "  market   dz  "))
    }

    @Test
    fun `a genuinely different word still misses`() {
        assertFalse(ArabicText.matches("شلبي للتسوق", "تميم"))
    }

    @Test
    fun `an empty query matches everything`() {
        assertTrue(ArabicText.matches("أي محل", ""))
        assertTrue(ArabicText.matches("أي محل", "   "))
    }
}
