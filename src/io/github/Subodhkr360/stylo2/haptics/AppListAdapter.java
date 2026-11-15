package io.github.Subodhkr360.stylo2.haptics;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Filter;
import android.widget.Filterable;
import android.widget.ImageView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

public class AppListAdapter extends BaseAdapter implements Filterable {

    public static class AppEntry {
        public String label;
        public String packageName;
        public Drawable icon;
        public boolean isSelected;
        public boolean isSystem;

        public AppEntry(String label, String packageName, Drawable icon, boolean isSelected, boolean isSystem) {
            this.label = label;
            this.packageName = packageName;
            this.icon = icon;
            this.isSelected = isSelected;
            this.isSystem = isSystem;
        }

        public AppEntry(String label, String packageName, Drawable icon, boolean isSelected) {
            this(label, packageName, icon, isSelected, false);
        }
    }

    public interface OnAppProfileClickListener {
        void onConfigureProfile(AppEntry entry);
        void onSelectionChanged();
    }

    private final Context mContext;
    private final List<AppEntry> mOriginalList;
    private List<AppEntry> mFilteredList;
    private final StylusHapticsManager mManager;
    private final OnAppProfileClickListener mListener;

    public AppListAdapter(Context context, List<AppEntry> apps, StylusHapticsManager manager, OnAppProfileClickListener listener) {
        mContext = context;
        mOriginalList = new ArrayList<>(apps);
        mFilteredList = new ArrayList<>(apps);
        mManager = manager;
        mListener = listener;
    }

    @Override
    public int getCount() {
        return mFilteredList.size();
    }

    @Override
    public AppEntry getItem(int position) {
        return mFilteredList.get(position);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    private static class ViewHolder {
        ImageView icon;
        TextView name;
        TextView profileSummary;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        ViewHolder holder;
        if (convertView == null) {
            convertView = LayoutInflater.from(mContext).inflate(R.layout.item_app, parent, false);
            holder = new ViewHolder();
            holder.icon = convertView.findViewById(R.id.app_icon);
            holder.name = convertView.findViewById(R.id.app_name);
            holder.profileSummary = convertView.findViewById(R.id.app_profile_summary);
            convertView.setTag(holder);
        } else {
            holder = (ViewHolder) convertView.getTag();
        }

        final AppEntry entry = getItem(position);
        holder.name.setText(entry.label);
        if (entry.icon != null) {
            holder.icon.setImageDrawable(entry.icon);
        }

        // Load profile
        StylusHapticsManager.AppProfile profile = mManager.getAppProfile(entry.packageName);
        if (profile.enabled) {
            holder.profileSummary.setText(getProfileSummaryText(profile));
            holder.profileSummary.setTextColor(Color.parseColor("#0084FF"));
        } else {
            holder.profileSummary.setText("Off");
            holder.profileSummary.setTextColor(Color.parseColor("#8E8E93"));
        }

        convertView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mListener != null) {
                    mListener.onConfigureProfile(entry);
                }
            }
        });

        return convertView;
    }

    private String getProfileSummaryText(StylusHapticsManager.AppProfile profile) {
        switch (profile.pattern) {
            case StylusHapticsManager.PATTERN_PENCIL: return "Pencil";
            case StylusHapticsManager.PATTERN_ERASER: return "Eraser";
            case StylusHapticsManager.PATTERN_BALLPOINT: return "Ballpoint";
            case StylusHapticsManager.PATTERN_FOUNTAIN: return "Fountain";
            default: return "Pencil";
        }
    }

    @Override
    public Filter getFilter() {
        return new Filter() {
            @Override
            protected FilterResults performFiltering(CharSequence constraint) {
                FilterResults results = new FilterResults();
                if (constraint == null || constraint.length() == 0) {
                    results.values = new ArrayList<>(mOriginalList);
                    results.count = mOriginalList.size();
                } else {
                    String query = constraint.toString().toLowerCase().trim();
                    List<AppEntry> filtered = new ArrayList<>();
                    for (AppEntry entry : mOriginalList) {
                        if (entry.label.toLowerCase().contains(query) || entry.packageName.toLowerCase().contains(query)) {
                            filtered.add(entry);
                        }
                    }
                    results.values = filtered;
                    results.count = filtered.size();
                }
                return results;
            }

            @SuppressWarnings("unchecked")
            @Override
            protected void publishResults(CharSequence constraint, FilterResults results) {
                if (results.values != null) {
                    mFilteredList = (List<AppEntry>) results.values;
                    notifyDataSetChanged();
                }
            }
        };
    }
}
