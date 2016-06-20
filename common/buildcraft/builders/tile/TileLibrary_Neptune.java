package buildcraft.builders.tile;

import java.io.IOException;
import java.time.LocalDateTime;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.PacketBuffer;
import net.minecraft.util.ITickable;

import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import net.minecraftforge.items.IItemHandlerModifiable;

import buildcraft.builders.gui.GuiBlueprintLibrary;
import buildcraft.lib.BCLibDatabase;
import buildcraft.lib.library.LibraryEntryHeader;
import buildcraft.lib.library.book.LibraryEntryBook;
import buildcraft.lib.misc.MessageUtil;
import buildcraft.lib.misc.NBTUtils;
import buildcraft.lib.permission.PlayerOwner;
import buildcraft.lib.tile.TileBCInventory_Neptune;
import buildcraft.lib.tile.item.ItemHandlerManager.EnumAccess;

public class TileLibrary_Neptune extends TileBCInventory_Neptune implements ITickable {
    public static final int NET_SELECTED = 20;

    public enum LibSlot {
        SAVE_IN,
        SAVE_OUT,
        LOAD_IN,
        LOAD_OUT
    }

    // TODO: some sort of library entry database
    // -- sync server <-> client
    // -- load disk <-> db
    // -- read + writers

    public final IItemHandlerModifiable inv;
    public int selected = -1;

    public TileLibrary_Neptune() {
        inv = addInventory("inv", 4, EnumAccess.NONE);
    }

    @Override
    public void update() {
        if (worldObj.isRemote) {
            return;
        }

        ItemStack saving = get(LibSlot.SAVE_IN);
        if (get(LibSlot.SAVE_OUT) == null && saving != null) {
            if (saving.getItem() == Items.WRITTEN_BOOK) {

                LibraryEntryBook data = LibraryEntryBook.create(saving);
                NBTTagCompound nbt = NBTUtils.getItemData(saving);
                if (data != null && nbt != null) {
                    PlayerOwner author = PlayerOwner.lookup(nbt.getString("author"));
                    String title = nbt.getString("title");

                    LibraryEntryHeader header = new LibraryEntryHeader(title, LibraryEntryBook.KIND, LocalDateTime.of(2016, 1, 1, 0, 0), author);

                    BCLibDatabase.LOCAL_DB.addNew(header, data);

                    set(LibSlot.SAVE_IN, null);
                    set(LibSlot.SAVE_OUT, saving);

                    BCLibDatabase.fillEntries();
                }
            }
        }

        ItemStack loading = get(LibSlot.LOAD_IN);
        if (get(LibSlot.LOAD_OUT) == null && loading != null) {

        }
    }

    public ItemStack get(LibSlot slot) {
        return inv.getStackInSlot(slot.ordinal());
    }

    public void set(LibSlot slot, ItemStack stack) {
        inv.setStackInSlot(slot.ordinal(), stack);
    }

    @Override
    public void writePayload(int id, PacketBuffer buffer, Side side) {
        super.writePayload(id, buffer, side);

        if (id == NET_GUI_DATA || id == NET_SELECTED) {
            buffer.writeInt(selected);
        }
    }

    @Override
    public void readPayload(int id, PacketBuffer buffer, Side side) throws IOException {
        super.readPayload(id, buffer, side);

        if (id == NET_GUI_DATA || id == NET_SELECTED) {
            selected = buffer.readInt();
            if (side == Side.SERVER) {
                MessageUtil.doDelayed(() -> {
                    sendNetworkUpdate(NET_SELECTED);
                });
            } else if (side == Side.CLIENT) {
                updateSelected();
            }
        }
    }

    @SideOnly(Side.CLIENT)
    private void updateSelected() {
        GuiScreen currentGui = Minecraft.getMinecraft().currentScreen;
        if (currentGui instanceof GuiBlueprintLibrary) {
            GuiBlueprintLibrary guiBptLib = (GuiBlueprintLibrary) currentGui;
            if (guiBptLib.container.tile == this) {
                guiBptLib.selected = selected;
            }
        }
    }
}
