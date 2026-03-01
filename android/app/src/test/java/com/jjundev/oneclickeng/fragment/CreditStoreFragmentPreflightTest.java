package com.jjundev.oneclickeng.fragment;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.jjundev.oneclickeng.billing.CreditPurchaseVerifier;
import org.junit.Test;

public class CreditStoreFragmentPreflightTest {

  @Test
  public void getPreflightBlockMessage_verifyUrlMissing_blocksLaunch() {
    String message = CreditStoreFragment.getPreflightBlockMessage("", true, true, true);

    assertNotNull(message);
  }

  @Test
  public void getPreflightBlockMessage_userSignedOut_blocksLaunch() {
    String message =
        CreditStoreFragment.getPreflightBlockMessage("https://example.com/verify", false, true, true);

    assertNotNull(message);
  }

  @Test
  public void getPreflightBlockMessage_billingNotReady_blocksLaunch() {
    String message =
        CreditStoreFragment.getPreflightBlockMessage("https://example.com/verify", true, false, true);

    assertNotNull(message);
  }

  @Test
  public void getPreflightBlockMessage_productNotReady_blocksLaunch() {
    String message =
        CreditStoreFragment.getPreflightBlockMessage("https://example.com/verify", true, true, false);

    assertNotNull(message);
  }

  @Test
  public void getPreflightBlockMessage_allReady_allowsLaunch() {
    String message =
        CreditStoreFragment.getPreflightBlockMessage("https://example.com/verify", true, true, true);

    assertNull(message);
  }

  @Test
  public void shouldKeepPendingPurchase_retryableStatus_returnsTrue() {
    assertTrue(
        CreditStoreFragment.shouldKeepPendingPurchase(
            CreditPurchaseVerifier.VerificationStatus.NETWORK_ERROR));
    assertTrue(
        CreditStoreFragment.shouldKeepPendingPurchase(
            CreditPurchaseVerifier.VerificationStatus.SERVER_ERROR));
    assertTrue(
        CreditStoreFragment.shouldKeepPendingPurchase(
            CreditPurchaseVerifier.VerificationStatus.CONFIG_ERROR));
    assertTrue(
        CreditStoreFragment.shouldKeepPendingPurchase(
            CreditPurchaseVerifier.VerificationStatus.AUTH_ERROR));
    assertTrue(
        CreditStoreFragment.shouldKeepPendingPurchase(
            CreditPurchaseVerifier.VerificationStatus.PENDING));
  }

  @Test
  public void shouldKeepPendingPurchase_nonRetryableStatus_returnsFalse() {
    assertFalse(
        CreditStoreFragment.shouldKeepPendingPurchase(
            CreditPurchaseVerifier.VerificationStatus.GRANTED));
    assertFalse(
        CreditStoreFragment.shouldKeepPendingPurchase(
            CreditPurchaseVerifier.VerificationStatus.ALREADY_GRANTED));
    assertFalse(
        CreditStoreFragment.shouldKeepPendingPurchase(
            CreditPurchaseVerifier.VerificationStatus.REJECTED));
    assertFalse(
        CreditStoreFragment.shouldKeepPendingPurchase(
            CreditPurchaseVerifier.VerificationStatus.INVALID));
  }
}

