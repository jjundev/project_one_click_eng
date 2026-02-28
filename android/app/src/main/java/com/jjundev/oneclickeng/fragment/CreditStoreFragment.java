package com.jjundev.oneclickeng.fragment;

import android.os.Bundle;
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
import com.jjundev.oneclickeng.R;
import java.util.ArrayList;
import java.util.List;

public class CreditStoreFragment extends Fragment {

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

    RecyclerView rvScripts = view.findViewById(R.id.rv_scripts);
    rvScripts.setLayoutManager(new GridLayoutManager(requireContext(), 2));
    rvScripts.setNestedScrollingEnabled(false);
    rvScripts.setAdapter(new CreditStoreAdapter(createProducts()));
  }

  @NonNull
  private List<CreditProduct> createProducts() {
    List<CreditProduct> products = new ArrayList<>();
    products.add(new CreditProduct("10 크레딧", "credit_10", "300원"));
    products.add(new CreditProduct("20 크레딧", "credit_20", "500원"));
    products.add(new CreditProduct("50 크레딧", "credit_50", "1000원"));
    return products;
  }

  private static class CreditStoreAdapter
      extends RecyclerView.Adapter<CreditStoreAdapter.CreditStoreViewHolder> {

    private final List<CreditProduct> items;

    CreditStoreAdapter(@NonNull List<CreditProduct> items) {
      this.items = items;
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
      holder.card.setOnClickListener(
          v -> Toast.makeText(v.getContext(), "구매 기능 준비 중입니다.", Toast.LENGTH_SHORT).show());
    }

    @Override
    public int getItemCount() {
      return items.size();
    }

    static class CreditStoreViewHolder extends RecyclerView.ViewHolder {
      private final View card;
      private final TextView tvProductName;
      private final TextView tvProductId;
      private final TextView tvPrice;

      CreditStoreViewHolder(@NonNull View itemView) {
        super(itemView);
        card = itemView.findViewById(R.id.card_credit_product);
        tvProductName = itemView.findViewById(R.id.tv_card_title);
        tvProductId = itemView.findViewById(R.id.tv_card_subtitle);
        tvPrice = itemView.findViewById(R.id.tv_card_price);
      }
    }
  }

  private static class CreditProduct {
    private final String productName;
    private final String productId;
    private final String priceText;

    CreditProduct(
        @NonNull String productName, @NonNull String productId, @NonNull String priceText) {
      this.productName = productName;
      this.productId = productId;
      this.priceText = priceText;
    }
  }
}
