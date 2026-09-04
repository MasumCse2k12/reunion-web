package bd.sammalani.alumni.ui.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

import bd.sammalani.alumni.R;
import bd.sammalani.alumni.model.Person;
import bd.sammalani.alumni.util.AvatarView;

public class MemberAdapter extends RecyclerView.Adapter<MemberAdapter.VH> {

    public interface OnMemberClickListener {
        void onMemberClick(Person person);
    }

    private List<Person> data;
    private final boolean bn;
    private final OnMemberClickListener listener;

    public MemberAdapter(List<Person> data, boolean bn, OnMemberClickListener listener) {
        this.data     = data;
        this.bn       = bn;
        this.listener = listener;
    }

    public void setData(List<Person> newData) {
        this.data = newData;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_member, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        Person p = data.get(position);
        h.tvName.setText(p.displayName());

        String sub = p.occupation != null && !p.occupation.isEmpty() ? p.occupation : "";
        if (p.city != null && !p.city.isEmpty()) {
            sub = sub.isEmpty() ? p.city : sub + " · " + p.city;
        }
        h.tvSub.setText(sub);

        if (p.deceased) {
            h.tvTag.setVisibility(View.VISIBLE);
            h.tvTag.setText(h.itemView.getContext().getString(R.string.batches_memorial));
            h.tvTag.setBackgroundResource(R.drawable.bg_tag_gold);
            h.tvTag.setTextColor(androidx.core.content.ContextCompat.getColor(h.itemView.getContext(), R.color.ink_700));
        } else if ("SEEDED".equals(p.status)) {
            h.tvTag.setVisibility(View.VISIBLE);
            h.tvTag.setText(h.itemView.getContext().getString(R.string.signup_not_joined));
            h.tvTag.setBackgroundResource(R.drawable.bg_tag_red);
            h.tvTag.setTextColor(androidx.core.content.ContextCompat.getColor(h.itemView.getContext(), R.color.red_500));
        } else if ("CLAIMED".equals(p.status)) {
            h.tvTag.setVisibility(View.VISIBLE);
            h.tvTag.setText(h.itemView.getContext().getString(R.string.signup_already_joined));
            h.tvTag.setBackgroundResource(R.drawable.bg_tag_brand);
            h.tvTag.setTextColor(androidx.core.content.ContextCompat.getColor(h.itemView.getContext(), R.color.brand_700));
        } else {
            h.tvTag.setVisibility(View.GONE);
        }

        AvatarView.load(h.itemView.getContext(), h.ivAvatar, p.photoUrl, p.displayName());

        if (listener != null) {
            h.itemView.setOnClickListener(v -> listener.onMemberClick(p));
        }
    }

    @Override
    public int getItemCount() { return data.size(); }

    static class VH extends RecyclerView.ViewHolder {
        ImageView ivAvatar;
        TextView tvName, tvSub, tvTag;

        VH(View v) {
            super(v);
            ivAvatar = v.findViewById(R.id.ivAvatar);
            tvName   = v.findViewById(R.id.tvName);
            tvSub    = v.findViewById(R.id.tvSub);
            tvTag    = v.findViewById(R.id.tvTag);
        }
    }
}
