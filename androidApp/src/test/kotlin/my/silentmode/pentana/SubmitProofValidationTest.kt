package my.silentmode.pentana

import my.silentmode.pentana.feature.bills.canSubmitProof
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SubmitProofValidationTest {
    @Test fun valid_when_amount_and_photo() = assertTrue(canSubmitProof("70.00", hasPhoto = true))

    @Test fun invalid_without_amount() = assertFalse(canSubmitProof("", hasPhoto = true))

    @Test fun invalid_without_photo() = assertFalse(canSubmitProof("70.00", hasPhoto = false))
}
