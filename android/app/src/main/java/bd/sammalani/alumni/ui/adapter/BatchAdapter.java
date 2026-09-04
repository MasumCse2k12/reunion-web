package bd.sammalani.alumni.ui.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

import bd.sammalani.alumni.R;
import bd.sammalani.alumni.model.Batch;
import bd.sammalani.alumni.util.Fmt;

public class BatchAdapter extends RecyclerView.Adapter<BatchAdapter.VH> {

    public interface OnBatchClickListener {
        void onBatchClick(Batch batch);
    }

    private List<Batch> data;
    private final boolean bn;
    private final int myBatchYear;
    private final OnBatchClickListener listener;

    public BatchAdapter(List<Batch> data, boolean bn, int myBatchYear, OnBatchClickListener listener) {
        this.data        = data;
        this.bn          = bn;
        this.myBatchYear = myBatchYear;
        this.listener    = listener;
    }

    public void setData(List<Batch> newData) {
        this.data = newData;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_batch, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        Batch b   = data.get(position);
        int found   = b.claimedCount;
        int total   = b.rosterCount;
        int missing = total - found;

        h.tvYear.setText(Fmt.year(b.year, bn));
        h.tvMemberCount.setText(Fmt.number(total, bn) + " "
                + h.itemView.getContext().getString(R.string.batches_members));
        h.tvFound.setText(Fmt.number(found, bn) + " "
                + h.itemView.getContext().getString(R.string.batches_found));
        h.tvMissing.setText(Fmt.number(missing, bn) + " "
                + h.itemView.getContext().getString(R.string.batches_missing));
        h.progress.setProgress(b.percent());
        h.tvYourBatch.setVisibility(b.year == myBatchYear ? View.VISIBLE : View.GONE);
        h.itemView.setOnClickListener(v -> listener.onBatchClick(b));
    }

    @Override
    public int getItemCount() { return data != null ? data.size() : 0; }

    static class VH extends RecyclerView.ViewHolder {
        TextView tvYear, tvMemberCount, tvFound, tvMissing, tvYourBatch;
        ProgressBar progress;

        VH(View v) {
            super(v);
            tvYear        = v.findViewById(R.id.tvYear);
            tvMemberCount = v.findViewById(R.id.tvMemberCount);
            tvFound       = v.findViewById(R.id.tvFound);
            tvMissing     = v.findViewById(R.id.tvMissing);
            tvYourBatch   = v.findViewById(R.id.tvYourBatch);
            progress      = v.findViewById(R.id.progressBatch);
        }
    }
}
