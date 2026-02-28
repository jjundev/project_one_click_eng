package com.jjundev.oneclickeng.fragment;

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.android.billingclient.api.BillingClient;
import com.android.billingclient.api.BillingResult;
import com.android.billingclient.api.ProductDetails;
import com.android.billingclient.api.Purchase;
import com.jjundev.oneclickeng.BuildConfig;
import com.jjundev.oneclickeng.R;
import com.jjundev.oneclickeng.billing.CreditPurchaseStore;
import com.jjundev.oneclickeng.billing.CreditPurchaseVerifier;
import com.jjundev.oneclickeng.billing.PlayBillingManager;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class CreditStoreFragment extends Fragment {
  private static final String TAG = "CreditStoreFragment";

  private static final String PRODUCT_CREDIT_10 = "credit_10";
  private static final String PRODUCT_CREDIT_20 = "credit_20";
  private static final String PRODUCT_CREDIT_50 = "credit_50";
  private static final List<String> CREDIT_PRODUCT_IDS =
      Arrays.asList(PRODUCT_CREDIT_10, PRODUCT_CREDIT_20, PRODUCT_CREDIT_50);

  private static final String PRICE_LOADING = "가격 확인 중...";
  private static final String PRICE_UNAVAILABLE = "구매 준비 중";

  @Nullable private RecyclerView rvProducts;
  @Nullable private CreditStoreAdapter adapter;
  @Nullable private PlayBillingManager billingManager;
  @Nullable private CreditPurchaseStore purchaseStore;
  @Nullable private CreditPurchaseVerifier purchaseVerifier;

  @NonNull private final List<CreditProduct> products = new ArrayList<>();
  @NonNull private final Set<String> verificationInFlightTokens = new HashSet<>();

  private boolean billingReady;
  private boolean billingUnavailableNotified;

  @Nullable
  @Override
  public View onCreateView(
      @NonNull LayoutInflater inflater,
      @Nullable ViewGroup container,
      @Nullable Bundle savedInstanceState) {
    return inflater.inflate(R.layout.fragment_credit_store, container, false);
  }

  @Override
  public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
    super.onViewCreated(view, savedInstanceState);
    initializeProducts();

    purchaseStore = new CreditPurchaseStore(requireContext());
    purchaseVerifier = new CreditPurchaseVerifier(BuildConfig.CREDIT_BILLING_VERIFY_URL);

    rvProducts = view.findViewById(R.id.rv_scripts);
    adapter = new CreditStoreAdapter(products, this::onCreditProductClicked);
    rvProducts.setLayoutManager(new GridLayoutManager(requireContext(), 2));
    rvProducts.setNestedScrollingEnabled(false);
    rvProducts.setAdapter(adapter);

    billingManager = new PlayBillingManager(requireContext(), this::onPurchasesUpdated);
    billingManager.startConnection(
        new PlayBillingManager.ConnectionListener() {
          @Override
          public void onBillingReady() {
            billingReady = true;
            billingUnavailableNotified = false;
            requestProductDetails();
            recoverOwnedPurchasesAndProcessPending();
          }

          @Override
          public void onBillingDisconnected() {
            billingReady = false;
            notifyAdapterChanged();
            logDebug("Billing service disconnected.");
          }

          @Override
          public void onBillingUnavailable(@NonNull BillingResult billingResult) {
            billingReady = false;
            notifyAdapterChanged();
            if (!billingUnavailableNotified) {
              billingUnavailableNotified = true;
              showToastSafe("결제 서비스를 사용할 수 없어요. 잠시 후 다시 시도해주세요.");
            }
            logDebug(
                "Billing unavailable: "
                    + billingResult.getResponseCode()
                    + " / "
                    + billingResult.getDebugMessage());
          }
        });
  }

  @Override
  public void onResume() {
    super.onResume();
    recoverOwnedPurchasesAndProcessPending();
  }

  @Override
  public void onDestroyView() {
    super.onDestroyView();
    rvProducts = null;
    adapter = null;
  }

  @Override
  public void onDestroy() {
    super.onDestroy();
    if (billingManager != null) {
      billingManager.endConnection();
      billingManager = null;
    }
  }

  private void initializeProducts() {
    products.clear();
    products.add(new CreditProduct("10 크레딧", PRODUCT_CREDIT_10, "300원", PRICE_LOADING));
    products.add(new CreditProduct("20 크레딧", PRODUCT_CREDIT_20, "500원", PRICE_LOADING));
    products.add(new CreditProduct("50 크레딧", PRODUCT_CREDIT_50, "1000원", PRICE_LOADING));
  }

  private void requestProductDetails() {
    PlayBillingManager manager = billingManager;
    if (manager == null || !billingReady) {
      return;
    }

    manager.queryInAppProductDetails(
        CREDIT_PRODUCT_IDS,
        new PlayBillingManager.ProductDetailsListener() {
          @Override
          public void onLoaded(@NonNull Map<String, ProductDetails> productDetailsById) {
            for (CreditProduct product : products) {
              ProductDetails details = productDetailsById.get(product.productId);
              product.productDetails = details;
              product.priceText = resolveFormattedPrice(details, product.fallbackPriceText);
            }
            notifyAdapterChanged();
          }

          @Override
          public void onFailed(@NonNull BillingResult billingResult) {
            for (CreditProduct product : products) {
              product.productDetails = null;
              product.priceText = PRICE_UNAVAILABLE;
            }
            notifyAdapterChanged();
            logDebug(
                "Failed to query product details: "
                    + billingResult.getResponseCode()
                    + " / "
                    + billingResult.getDebugMessage());
            showToastSafe("상품 정보를 불러오지 못했어요. 잠시 후 다시 시도해주세요.");
          }
        });
  }

  @NonNull
  private static String resolveFormattedPrice(
      @Nullable ProductDetails productDetails, @NonNull String fallbackPrice) {
    if (productDetails == null) {
      return fallbackPrice;
    }
    ProductDetails.OneTimePurchaseOfferDetails oneTimeOffer =
        productDetails.getOneTimePurchaseOfferDetails();
    if (oneTimeOffer == null) {
      return fallbackPrice;
    }
    String formattedPrice = oneTimeOffer.getFormattedPrice();
    if (formattedPrice == null || formattedPrice.trim().isEmpty()) {
      return fallbackPrice;
    }
    return formattedPrice.trim();
  }

  private void onCreditProductClicked(@NonNull CreditProduct product) {
    if (!isAdded()) {
      return;
    }

    if (!billingReady || billingManager == null) {
      showToastSafe("결제 서비스를 준비 중이에요.");
      return;
    }

    if (product.productDetails == null) {
      showToastSafe("상품 정보를 불러오는 중이에요.");
      return;
    }

    Activity hostActivity = getActivity();
    if (hostActivity == null) {
      return;
    }

    BillingResult launchResult = billingManager.launchBillingFlow(hostActivity, product.productDetails);
    int responseCode = launchResult.getResponseCode();
    if (responseCode == BillingClient.BillingResponseCode.OK
        || responseCode == BillingClient.BillingResponseCode.USER_CANCELED) {
      return;
    }

    logDebug(
        "Failed to launch billing flow: "
            + responseCode
            + " / "
            + launchResult.getDebugMessage());
    showToastSafe("결제를 시작하지 못했어요. 잠시 후 다시 시도해주세요.");
  }

  private void onPurchasesUpdated(
      @NonNull BillingResult billingResult, @Nullable List<Purchase> purchases) {
    int responseCode = billingResult.getResponseCode();
    if (responseCode == BillingClient.BillingResponseCode.OK && purchases != null) {
      for (Purchase purchase : purchases) {
        handleIncomingPurchase(purchase, true);
      }
      processPendingPurchases();
      return;
    }

    if (responseCode == BillingClient.BillingResponseCode.USER_CANCELED) {
      showToastSafe("결제가 취소되었어요.");
      return;
    }

    if (responseCode == BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED) {
      recoverOwnedPurchasesAndProcessPending();
      return;
    }

    logDebug(
        "onPurchasesUpdated failed: "
            + responseCode
            + " / "
            + billingResult.getDebugMessage());
    showToastSafe("결제 처리 중 오류가 발생했어요.");
  }

  private void recoverOwnedPurchasesAndProcessPending() {
    PlayBillingManager manager = billingManager;
    if (manager == null || !billingReady) {
      return;
    }

    manager.queryInAppPurchases(
        (billingResult, purchases) -> {
          if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK) {
            for (Purchase purchase : purchases) {
              handleIncomingPurchase(purchase, false);
            }
          } else {
            logDebug(
                "queryInAppPurchases failed: "
                    + billingResult.getResponseCode()
                    + " / "
                    + billingResult.getDebugMessage());
          }
          processPendingPurchases();
        });
  }

  private void handleIncomingPurchase(@NonNull Purchase purchase, boolean fromUserFlow) {
    int state = purchase.getPurchaseState();
    if (state == Purchase.PurchaseState.PENDING) {
      enqueuePendingPurchase(purchase);
      if (fromUserFlow) {
        showToastSafe("결제가 승인 대기 중이에요. 승인 완료 후 자동으로 반영돼요.");
      }
      return;
    }

    if (state == Purchase.PurchaseState.PURCHASED) {
      enqueuePendingPurchase(purchase);
      if (fromUserFlow) {
        showToastSafe("구매를 확인하고 있어요...");
      }
      return;
    }

    logDebug("Ignoring purchase with state=" + state + ", token=" + maskToken(purchase.getPurchaseToken()));
  }

  private void enqueuePendingPurchase(@NonNull Purchase purchase) {
    CreditPurchaseStore store = purchaseStore;
    if (store == null) {
      return;
    }

    List<String> productIds = purchase.getProducts();
    if (productIds == null || productIds.isEmpty()) {
      return;
    }

    String packageName = requireContext().getPackageName();
    for (String productId : productIds) {
      String safeProductId = productId == null ? "" : productId.trim();
      if (safeProductId.isEmpty()) {
        continue;
      }
      CreditPurchaseStore.PendingPurchase pendingPurchase =
          new CreditPurchaseStore.PendingPurchase(
              packageName,
              safeProductId,
              purchase.getPurchaseToken(),
              purchase.getOrderId(),
              purchase.getPurchaseTime(),
              purchase.getQuantity(),
              purchase.getPurchaseState());
      store.upsertPendingPurchase(pendingPurchase);
    }
  }

  private void processPendingPurchases() {
    CreditPurchaseStore store = purchaseStore;
    CreditPurchaseVerifier verifier = purchaseVerifier;
    if (store == null || verifier == null) {
      return;
    }

    List<CreditPurchaseStore.PendingPurchase> pendingPurchases = store.getPendingPurchases();
    for (CreditPurchaseStore.PendingPurchase pendingPurchase : pendingPurchases) {
      String token = pendingPurchase.getPurchaseToken();
      if (token.isEmpty() || verificationInFlightTokens.contains(token)) {
        continue;
      }
      verificationInFlightTokens.add(token);
      verifier.verifyPurchase(
          pendingPurchase, result -> handleVerificationResult(pendingPurchase, result));
    }
  }

  private void handleVerificationResult(
      @NonNull CreditPurchaseStore.PendingPurchase pendingPurchase,
      @NonNull CreditPurchaseVerifier.VerificationResult verificationResult) {
    String token = pendingPurchase.getPurchaseToken();
    switch (verificationResult.status) {
      case GRANTED:
      case ALREADY_GRANTED:
        consumeAfterVerifiedGrant(pendingPurchase, verificationResult);
        return;
      case PENDING:
        verificationInFlightTokens.remove(token);
        return;
      case REJECTED:
      case INVALID:
        verificationInFlightTokens.remove(token);
        showToastSafe("결제 검증에 실패했어요. 고객센터로 문의해주세요.");
        logDebug(
            "Verification rejected for token="
                + maskToken(token)
                + ", status="
                + verificationResult.status
                + ", message="
                + verificationResult.message);
        return;
      case AUTH_ERROR:
      case CONFIG_ERROR:
      case NETWORK_ERROR:
      case SERVER_ERROR:
      default:
        verificationInFlightTokens.remove(token);
        logDebug(
            "Verification retryable failure for token="
                + maskToken(token)
                + ", status="
                + verificationResult.status
                + ", message="
                + verificationResult.message);
    }
  }

  private void consumeAfterVerifiedGrant(
      @NonNull CreditPurchaseStore.PendingPurchase pendingPurchase,
      @NonNull CreditPurchaseVerifier.VerificationResult verificationResult) {
    PlayBillingManager manager = billingManager;
    CreditPurchaseStore store = purchaseStore;
    if (manager == null || store == null) {
      verificationInFlightTokens.remove(pendingPurchase.getPurchaseToken());
      return;
    }

    manager.consumePurchase(
        pendingPurchase.getPurchaseToken(),
        (billingResult, purchaseToken) -> {
          verificationInFlightTokens.remove(purchaseToken);
          int responseCode = billingResult.getResponseCode();
          if (responseCode == BillingClient.BillingResponseCode.OK
              || responseCode == BillingClient.BillingResponseCode.ITEM_NOT_OWNED) {
            store.removeByPurchaseToken(purchaseToken);
            long grantedCredits = verificationResult.grantedCredits;
            if (grantedCredits > 0) {
              showToastSafe(grantedCredits + " 크레딧이 충전되었어요.");
            } else {
              showToastSafe("크레딧이 충전되었어요.");
            }
            return;
          }

          logDebug(
              "consumePurchase failed: token="
                  + maskToken(purchaseToken)
                  + ", code="
                  + responseCode
                  + ", message="
                  + billingResult.getDebugMessage());
        });
  }

  private void notifyAdapterChanged() {
    CreditStoreAdapter adapterRef = adapter;
    if (adapterRef != null) {
      adapterRef.notifyDataSetChanged();
    }
  }

  private void showToastSafe(@NonNull String message) {
    if (!isAdded()) {
      return;
    }
    Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show();
  }

  private void logDebug(@NonNull String message) {
    if (BuildConfig.DEBUG) {
      Log.d(TAG, message);
    }
  }

  @NonNull
  private static String maskToken(@NonNull String token) {
    String safe = token.trim();
    if (safe.length() <= 8) {
      return safe;
    }
    return safe.substring(0, 4) + "..." + safe.substring(safe.length() - 4);
  }

  private static class CreditStoreAdapter
      extends RecyclerView.Adapter<CreditStoreAdapter.CreditStoreViewHolder> {

    @NonNull private final OnProductClickListener clickListener;
    @NonNull private final List<CreditProduct> items;

    CreditStoreAdapter(
        @NonNull List<CreditProduct> items, @NonNull OnProductClickListener clickListener) {
      this.items = items;
      this.clickListener = clickListener;
    }

    @NonNull
    @Override
    public CreditStoreViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
      View view =
          LayoutInflater.from(parent.getContext())
              .inflate(R.layout.item_credit_store_product, parent, false);
      return new CreditStoreViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull CreditStoreViewHolder holder, int position) {
      CreditProduct item = items.get(position);
      holder.tvProductName.setText(item.productName);
      holder.tvProductId.setText(item.productId);
      holder.tvPrice.setText(item.priceText);
      holder.card.setOnClickListener(v -> clickListener.onProductClicked(item));
      holder.card.setAlpha(item.productDetails == null ? 0.8f : 1.0f);
    }

    @Override
    public int getItemCount() {
      return items.size();
    }

    static class CreditStoreViewHolder extends RecyclerView.ViewHolder {
      @NonNull private final View card;
      @NonNull private final TextView tvProductName;
      @NonNull private final TextView tvProductId;
      @NonNull private final TextView tvPrice;

      CreditStoreViewHolder(@NonNull View itemView) {
        super(itemView);
        card = itemView.findViewById(R.id.card_credit_product);
        tvProductName = itemView.findViewById(R.id.tv_card_title);
        tvProductId = itemView.findViewById(R.id.tv_card_subtitle);
        tvPrice = itemView.findViewById(R.id.tv_card_price);
      }
    }

    interface OnProductClickListener {
      void onProductClicked(@NonNull CreditProduct product);
    }
  }

  private static class CreditProduct {
    @NonNull private final String productName;
    @NonNull private final String productId;
    @NonNull private final String fallbackPriceText;
    @Nullable private ProductDetails productDetails;
    @NonNull private String priceText;

    CreditProduct(
        @NonNull String productName,
        @NonNull String productId,
        @NonNull String fallbackPriceText,
        @NonNull String initialPriceText) {
      this.productName = productName;
      this.productId = productId;
      this.fallbackPriceText = fallbackPriceText;
      this.priceText = initialPriceText;
    }
  }
}
