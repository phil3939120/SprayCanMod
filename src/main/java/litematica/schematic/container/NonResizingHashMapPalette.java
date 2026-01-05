package litematica.schematic.container;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;

import malilib.util.data.palette.BasePalette;
import malilib.util.data.palette.PaletteResizeHandler;

public class NonResizingHashMapPalette<T> extends BasePalette<T>
{
    protected final Object2IntOpenHashMap<T> valueToIdMap;

    public NonResizingHashMapPalette(int startingSize)
    {
        super(startingSize);

        this.valueToIdMap = new Object2IntOpenHashMap<>();
        this.valueToIdMap.defaultReturnValue(-1);
    }

    @Override
    public int idFor(T value)
    {
        int id = this.valueToIdMap.getInt(value);

        if (id == -1)
        {
            id = this.addNewValue(value);
        }

        return id;
    }

    @SuppressWarnings("unchecked")
    protected int addNewValue(T value)
    {
        int id = this.currentSize;
        this.valueToIdMap.put(value, this.currentSize);
        ++this.currentSize;

        if (id >= this.values.length)
        {
            T[] oldArr = this.values;
            this.values = (T[]) new Object[oldArr.length * 2];
            System.arraycopy(oldArr, 0, this.values, 0, oldArr.length);
        }

        this.values[id] = value;

        return id;
    }

    @Override
    public List<T> getMapping()
    {
        final int size = this.currentSize;
        List<T> list = new ArrayList<>(size);

        for (int id = 0; id < size; ++id)
        {
            list.add(this.values[id]);
        }

        return list;
    }

    @SuppressWarnings("unchecked")
    @Override
    public boolean setMapping(List<T> list)
    {
        final int size = list.size();

        if (size >= this.values.length)
        {
            this.values = (T[]) new Object[size];
        }

        this.valueToIdMap.clear();
        Arrays.fill(this.values, null);

        for (int id = 0; id < size; ++id)
        {
            T val = list.get(id);
            this.valueToIdMap.put(val, id);
            this.values[id] = val;
        }

        this.currentSize = size;

        return true;
    }

    @Override
    public boolean overrideMapping(int id, T value)
    {
        if (id >= 0 && id < this.currentSize)
        {
            this.values[id] = value;
            this.valueToIdMap.put(value, id);
            return true;
        }

        return false;
    }

    @Override
    public NonResizingHashMapPalette<T> copy(PaletteResizeHandler<T> resizeHandler)
    {
        NonResizingHashMapPalette<T> copy = new NonResizingHashMapPalette<>(this.values.length);
        copy.setMapping(this.getMapping());
        return copy;
    }
}
