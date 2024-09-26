package com.thebeyond.data.assets;

import com.thebeyond.TheBeyond;
import net.minecraft.data.PackOutput;
import net.neoforged.neoforge.client.model.generators.BlockStateProvider;
import net.neoforged.neoforge.common.data.ExistingFileHelper;

public class BlockStates extends BlockStateProvider {
    public BlockStates(PackOutput output, ExistingFileHelper exFileHelper) {
        super(output, TheBeyond.MODID, exFileHelper);
    }

    @Override
    protected void registerStatesAndModels() {

    }
}
