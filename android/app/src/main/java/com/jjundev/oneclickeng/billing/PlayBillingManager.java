package com.jjundev.oneclickeng.billing;

import android.app.Activity;
import android.content.Context;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.android.billingclient.api.BillingClient;
import com.android.billingclient.api.BillingClientStateListener;
import com.android.billingclient.api.BillingFlowParams;
import com.android.billingclient.api.BillingResult;
import com.android.billingclient.api.ConsumeParams;
import com.android.billingclient.api.PendingPurchasesParams;
import com.android.billingclient.api.ProductDetails;
import com.android.billingclient.api.Purchase;
import com.android.billingclient.api.PurchasesUpdatedListener;
import com.android.billingclient.api.QueryProductDetailsParams;
import com.android.billingclient.api.QueryPurchasesParams;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Thin wrapper around BillingClient for one-time INAPP credit products. */
public final class PlayBillingManager {
  @NonNull private final BillingClient billingClient;

  public PlayBillingManager(
      @NonNull Context context, @NonNull PurchasesUpdatedListener purchaseUpdateListener) {
    billingClient =
        BillingClient.newBuilder(context.getApplicationContext())
            .setListener(purchaseUpdateListener)
            .enablePendingPurchases(
                PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
            .build();
  }

  public boolean isReady() {
    return billingClient.isReady();
  }

  public void startConnection(@NonNull ConnectionListener listener) {
    if (billingClient.isReady()) {
      listener.onBillingReady();
      return;
    }

    billingClient.startConnection(
        new BillingClientStateListener() {
          @Override
          public void onBillingSetupFinished(@NonNull BillingResult billingResult) {
            if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK) {
              listener.onBillingReady();
              return;
            }
            listener.onBillingUnavailable(billingResult);
          }

          @Override
          public void onBillingServiceDisconnected() {
            listener.onBillingDisconnected();
          }
        });
  }

  public void endConnection() {
    billingClient.endConnection();
  }

  public void queryInAppProductDetails(
      @NonNull List<String> productIds, @NonNull ProductDetailsListener listener) {
    if (!billingClient.isReady()) {
      listener.onFailed(buildNotReadyResult("BillingClient not ready"));
      return;
    }

    List<QueryProductDetailsParams.Product> products = new ArrayList<>();
    for (String productId : productIds) {
      String safeProductId = productId == null ? "" : productId.trim();
      if (safeProductId.isEmpty()) {
        continue;
      }
      products.add(
          QueryProductDetailsParams.Product.newBuilder()
              .setProductId(safeProductId)
              .setProductType(BillingClient.ProductType.INAPP)
              .build());
    }

    if (products.isEmpty()) {
      listener.onFailed(buildNotReadyResult("No product ids"));
      return;
    }

    QueryProductDetailsParams params =
        QueryProductDetailsParams.newBuilder().setProductList(products).build();
    billingClient.queryProductDetailsAsync(
        params,
        (billingResult, productDetailsResult) -> {
          if (billingResult.getResponseCode() != BillingClient.BillingResponseCode.OK) {
            listener.onFailed(billingResult);
            return;
          }
          Map<String, ProductDetails> byProductId = new LinkedHashMap<>();
          List<ProductDetails> productDetailsList =
              productDetailsResult != null ? productDetailsResult.getProductDetailsList() : null;
          if (productDetailsList != null) {
            for (ProductDetails details : productDetailsList) {
              byProductId.put(details.getProductId(), details);
            }
          }
          listener.onLoaded(byProductId);
        });
  }

  public void queryInAppPurchases(@NonNull InAppPurchasesListener listener) {
    if (!billingClient.isReady()) {
      listener.onResult(buildNotReadyResult("BillingClient not ready"), Collections.emptyList());
      return;
    }

    QueryPurchasesParams params =
        QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.INAPP).build();
    billingClient.queryPurchasesAsync(params, listener::onResult);
  }

  @NonNull
  public BillingResult launchBillingFlow(
      @NonNull Activity activity, @Nullable ProductDetails productDetails) {
    if (!billingClient.isReady()) {
      return buildNotReadyResult("BillingClient not ready");
    }
    if (productDetails == null) {
      return buildNotReadyResult("ProductDetails is null");
    }

    BillingFlowParams.ProductDetailsParams productParams =
        BillingFlowParams.ProductDetailsParams.newBuilder().setProductDetails(productDetails).build();
    BillingFlowParams params =
        BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(Collections.singletonList(productParams))
            .build();
    return billingClient.launchBillingFlow(activity, params);
  }

  public void consumePurchase(
      @NonNull String purchaseToken, @NonNull ConsumeResultListener consumeResultListener) {
    if (!billingClient.isReady()) {
      consumeResultListener.onConsumeResult(
          buildNotReadyResult("BillingClient not ready"), purchaseToken);
      return;
    }

    String safeToken = purchaseToken.trim();
    if (safeToken.isEmpty()) {
      consumeResultListener.onConsumeResult(buildNotReadyResult("Empty purchase token"), purchaseToken);
      return;
    }

    ConsumeParams params = ConsumeParams.newBuilder().setPurchaseToken(safeToken).build();
    billingClient.consumeAsync(
        params, (billingResult, outToken) -> consumeResultListener.onConsumeResult(billingResult, outToken));
  }

  @NonNull
  private static BillingResult buildNotReadyResult(@NonNull String message) {
    return BillingResult.newBuilder()
        .setResponseCode(BillingClient.BillingResponseCode.SERVICE_DISCONNECTED)
        .setDebugMessage(message)
        .build();
  }

  public interface ConnectionListener {
    void onBillingReady();

    void onBillingDisconnected();

    void onBillingUnavailable(@NonNull BillingResult billingResult);
  }

  public interface ProductDetailsListener {
    void onLoaded(@NonNull Map<String, ProductDetails> productDetailsById);

    void onFailed(@NonNull BillingResult billingResult);
  }

  public interface InAppPurchasesListener {
    void onResult(@NonNull BillingResult billingResult, @NonNull List<Purchase> purchases);
  }

  public interface ConsumeResultListener {
    void onConsumeResult(@NonNull BillingResult billingResult, @NonNull String purchaseToken);
  }
}
