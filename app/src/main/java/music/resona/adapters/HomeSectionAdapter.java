package music.resona.adapters;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

import music.resona.R;
import music.resona.online.bridge.models.HomeSectionResult;

/**
 * Adapter managing the vertical list of home feed sections.
 * 
 * <p>Each section displays:</p>
 * <ul>
 *   <li>A title TextView (e.g., "Quick picks", "Recommended albums")</li>
 *   <li>A horizontal RecyclerView of items (managed by HomeItemAdapter)</li>
 * </ul>
 * 
 * <p>Position mapping: Each section occupies 2 positions (title + items list)</p>
 */
public class HomeSectionAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static final int VIEW_TYPE_SECTION_TITLE = 0;
    private static final int VIEW_TYPE_SECTION_ITEMS = 1;
    private static final int ITEMS_PER_SECTION = 2;

    private final Context context;
    private final List<HomeSectionResult> sections;

    /**
     * Creates a new HomeSectionAdapter.
     * 
     * @param context the activity context
     * @param sections the list of home sections to display
     */
    public HomeSectionAdapter(@NonNull Context context, @NonNull List<HomeSectionResult> sections) {
        this.context = context;
        this.sections = sections;
    }

    /**
     * Determines the view type for the given position.
     * 
     * <p>Even positions (0, 2, 4...) are section titles.<br>
     * Odd positions (1, 3, 5...) are horizontal item lists.</p>
     * 
     * @param position the adapter position
     * @return the view type constant
     */
    @Override
    public int getItemViewType(int position) {
        return (position % ITEMS_PER_SECTION == 0) ? VIEW_TYPE_SECTION_TITLE : VIEW_TYPE_SECTION_ITEMS;
    }

    @Override
    public int getItemCount() {
        return sections.size() * ITEMS_PER_SECTION;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(context);
        
        if (viewType == VIEW_TYPE_SECTION_TITLE) {
            View view = inflater.inflate(R.layout.item_section_title, parent, false);
            return new TitleViewHolder(view);
        } else {
            View view = inflater.inflate(R.layout.item_section_list, parent, false);
            return new ItemsViewHolder(view);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        int sectionIndex = position / ITEMS_PER_SECTION;
        HomeSectionResult section = sections.get(sectionIndex);
        
        if (holder instanceof TitleViewHolder) {
            bindTitle((TitleViewHolder) holder, section);
        } else if (holder instanceof ItemsViewHolder) {
            bindItems((ItemsViewHolder) holder, section);
        }
    }
    
    /**
     * Binds section title data to the ViewHolder.
     * 
     * @param holder the title ViewHolder
     * @param section the section data
     */
    private void bindTitle(@NonNull TitleViewHolder holder, @NonNull HomeSectionResult section) {
        holder.tvSectionTitle.setText(section.getTitle());
        music.resona.utils.UiUXUtil.typeface(context, holder.tvSectionTitle, "akatski.ttf", android.graphics.Typeface.BOLD);
    }
    
    /**
     * Binds section items data to the ViewHolder.
     * 
     * @param holder the items ViewHolder
     * @param section the section data
     */
    private void bindItems(@NonNull ItemsViewHolder holder, @NonNull HomeSectionResult section) {
        HomeItemAdapter itemAdapter = new HomeItemAdapter(context, section.getItems());
        holder.rvHorizontal.setLayoutManager(
            new LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
        );
        holder.rvHorizontal.setAdapter(itemAdapter);
    }

    /**
     * ViewHolder for section title display.
     */
    static class TitleViewHolder extends RecyclerView.ViewHolder {
        final TextView tvSectionTitle;

        TitleViewHolder(@NonNull View itemView) {
            super(itemView);
            tvSectionTitle = itemView.findViewById(R.id.tvSectionTitle);
        }
    }

    /**
     * ViewHolder for horizontal items list display.
     */
    static class ItemsViewHolder extends RecyclerView.ViewHolder {
        final RecyclerView rvHorizontal;

        ItemsViewHolder(@NonNull View itemView) {
            super(itemView);
            rvHorizontal = itemView.findViewById(R.id.rvHorizontal);
        }
    }
}
