package io.github.Subodhkr360.stylo2.haptics;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Filter;
import android.widget.Filterable;
import android.widget.ImageView;
import android.widget.Switch;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

public class LandscapeAppAdapter extends BaseAdapter implements Filterable {

    private final Context mContext;
    private final List<AppListAdapter.AppEntry> mOriginalList;
    private List<AppListAdapter.AppEntry> mFilteredList;
    private final LandscapeViewManager mManager;

    public LandscapeAppAdapter(Context context, List<AppListAdapter.AppEntry> apps, LandscapeViewManager manager) {
        mContext = context;
        mOriginalList = new ArrayList<>(apps);
        mFilteredList = new ArrayList<>(apps);
        mManager = manager;
    }

    public void updateList(List<AppListAdapter.AppEntry> apps) {
        mOriginalList.clear();
        mOriginalList.addAll(apps);
        mFilteredList.clear();
        mFilteredList.addAll(apps);
        notifyDataSetChanged();
    }

    @Override
    public int getCount() {
        return mFilteredList.size();
    }

    @Override
    public AppListAdapter.AppEntry getItem(int position) {
        return mFilteredList.get(position);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    private static class ViewHolder {
        ImageView icon;
        TextView name;
        TextView pkg;
        Switch toggle;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        ViewHolder holder;
        if (convertView == null) {
            convertView = LayoutInflater.from(mContext).inflate(R.layout.item_landscape_app, parent, false);
            holder = new ViewHolder();
            holder.icon = convertView.findViewById(R.id.landscape_app_icon);
            holder.name = convertView.findViewById(R.id.landscape_app_name);
            holder.pkg = convertView.findViewById(R.id.landscape_app_package);
            holder.toggle = convertView.findViewById(R.id.landscape_app_switch);
            convertView.setTag(holder);
        } else {
            holder = (ViewHolder) convertView.getTag();
        }

        final AppListAdapter.AppEntry entry = getItem(position);
        holder.name.setText(entry.label);
        holder.pkg.setText(entry.packageName);
        if (entry.icon != null) {
            holder.icon.setImageDrawable(entry.icon);
        }

        boolean enabled = mManager.isLandscapeCompatEnabled(entry.packageName);
        holder.toggle.setOnCheckedChangeListener(null);
        holder.toggle.setChecked(enabled);

        holder.toggle.setOnCheckedChangeListener((buttonView, isChecked) -> {
            mManager.setLandscapeCompatEnabled(entry.packageName, isChecked);
        });

        convertView.setOnClickListener(v -> holder.toggle.toggle());

        return convertView;
    }

    @Override
    public Filter getFilter() {
        return new Filter() {
            @Override
            protected FilterResults performFiltering(CharSequence constraint) {
                FilterResults results = new FilterResults();
                if (constraint == null || constraint.length() == 0) {
                    results.values = mOriginalList;
                    results.count = mOriginalList.size();
                } else {
                    String query = constraint.toString().toLowerCase().trim();
                    List<AppListAdapter.AppEntry> filtered = new ArrayList<>();
                    for (AppListAdapter.AppEntry entry : mOriginalList) {
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
                mFilteredList = (List<AppListAdapter.AppEntry>) results.values;
                notifyDataSetChanged();
            }
        };
    }
}
