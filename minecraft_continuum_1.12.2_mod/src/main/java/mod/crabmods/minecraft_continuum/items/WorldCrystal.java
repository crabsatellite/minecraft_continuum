package mod.crabmods.minecraft_continuum.items;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import net.minecraft.client.resources.I18n;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ActionResult;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumHand;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

public class WorldCrystal extends Item {

  private final String scriptPath;

  public WorldCrystal(String scriptPath) {
    super();
    this.scriptPath = scriptPath;
  }

  @Override
  public ActionResult<ItemStack> onItemRightClick(World world, EntityPlayer player, EnumHand hand) {
    ItemStack itemStack = player.getHeldItem(hand);

    // Verify the script path before attempting any teleportation
    if (!isValidWorldDirectory(scriptPath)) {
      if (world.isRemote) {
        player.sendMessage(
            new TextComponentTranslation("message.minecraft_continuum.invalid_world_path"));
      }
      return new ActionResult<>(EnumActionResult.FAIL, itemStack);
    }

    if (!world.isRemote) {
      player.sendMessage(
          new TextComponentTranslation("message.minecraft_continuum.preparing_teleportation"));
    }

    // Run asynchronously to prevent blocking the game thread
    new Thread(
            () -> {
              try {
                if (!world.isRemote) {
                  String worldDirectory =
                      Paths.get(scriptPath).getParent().getParent().getFileName().toString();
                  player.sendMessage(
                      new TextComponentTranslation(
                          "message.minecraft_continuum.teleporting_to_world", worldDirectory));
                }
                if (world.isRemote) {
                  teleportAndRunScript(player);
                }
              } catch (IOException e) {
                player.sendMessage(
                    new TextComponentTranslation(
                        "message.minecraft_continuum.failed_to_execute_script", e.getMessage()));
                e.printStackTrace();
              }
            })
        .start();

    // Return interaction result
    return new ActionResult<>(EnumActionResult.SUCCESS, itemStack);
  }

  public void teleportAndRunScript(EntityPlayer player) throws IOException {
    World world = player.world;

    // Handle logic only on the client side
    if (!world.isRemote) {
      // Display a loading message in the HUD
      player.sendMessage(
          new TextComponentTranslation("message.minecraft_continuum.loading_new_world"));
    }

    if (world.isRemote) {
      // Run the batch file to load the new world
      runBatchFile(scriptPath);
      // Close current game instance (no direct equivalent in 1.12.2, just notify)
      player.sendMessage(
          new TextComponentTranslation("message.minecraft_continuum.client_closing"));
    }

    if (!world.isRemote) {
      // Notify the client that the world has been loaded
      player.sendMessage(
          new TextComponentTranslation("message.minecraft_continuum.new_world_loaded"));
    }
  }

  @Override
  @SideOnly(Side.CLIENT)
  public String getItemStackDisplayName(ItemStack stack) {
    // Extract the world directory from the script path
    Path path = Paths.get(scriptPath);
    String worldDirectory = path.getParent().getParent().getFileName().toString();

    // Use translation key with format argument
    return I18n.format("item.minecraft_continuum.world_crystal", worldDirectory);
  }

  @Override
  public boolean hasEffect(ItemStack stack) {
    // Show the item as shiny if the world directory is valid
    return isValidWorldDirectory(scriptPath);
  }

  private void runBatchFile(String batPath) throws IOException {
    // Execute the batch file
    ProcessBuilder builder = new ProcessBuilder("cmd.exe", "/c", batPath);

    // Redirect output and error to the console or a temporary file
    builder.redirectOutput(ProcessBuilder.Redirect.INHERIT);
    builder.redirectError(ProcessBuilder.Redirect.INHERIT);

    builder.start();
  }

  private boolean isValidWorldDirectory(String scriptPath) {
    // Check if the path exists and is a directory
    Path path = Paths.get(scriptPath).getParent().getParent();
    if (!Files.exists(path) || !Files.isDirectory(path)) {
      return false;
    }

    // Check for the presence of a ".minecraft" folder in the grandparent directory
    return Files.exists(path.resolve(".minecraft"));
  }
}