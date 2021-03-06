package com.theincgi.advancedMacros.asm;


import static org.objectweb.asm.Opcodes.BIPUSH;
import static org.objectweb.asm.Opcodes.IF_ICMPLE;
import static org.objectweb.asm.Opcodes.INVOKESTATIC;

import java.util.Arrays;

import org.luaj.vm2_v3_0_1.LuaValue;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.IntInsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

import com.theincgi.advancedMacros.misc.LuaTextComponentClickEvent;
import com.theincgi.advancedMacros.misc.Settings;
import com.theincgi.advancedMacros.misc.Utils;

import net.minecraft.client.gui.GuiNewChat;
import net.minecraft.launchwrapper.IClassTransformer;

//-Dfml.coreMods.load=com.theincgi.advancedMacros.asm.AdvancedMacrosCorePlugin
//as a VM argument for the launch config if you want this to apply when working in a dev env.
public class ChatLinesEditThingAndOthers implements IClassTransformer{
	/**
	 * {@link GuiNewChat}
	 * */
	public static String[] editedClasses = {
			"net.minecraft.client.gui.GuiNewChat",
			"net.minecraft.client.gui.GuiScreen",
			//,"io.netty.channel.local.LocalChannel"
	};

	@Override
	public byte[] transform(String name, String transformedName, byte[] basicClass) {
		if(basicClass == null) return null;

		boolean isObf = !name.equals(transformedName);
		AdvancedMacrosCorePlugin.isObfuscated |= isObf;
		int index = Arrays.asList(editedClasses).indexOf(transformedName);


		return index==-1 ? basicClass : transform(index, basicClass, isObf);
	}

	private static byte[] transform(int index, byte[] basicClass, boolean isObf) {
		System.out.printf("AdvancedMacros is transforming '%s'\n", editedClasses[index]);
		try {
			ClassNode	node   = new ClassNode();
			ClassReader reader = new ClassReader(basicClass);
			reader.accept(node, 0);


			switch (index) {
			case 0: //gui new chat
				transformGuiNewChat(node, isObf);
				break;
//			case 1:
			case 1:
				transformGuiScreenComponentClick(node, isObf);
//				transformLocalChannelWrite(node, isObf);
//				break;
			default:
				break;
			}


			ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);
			node.accept(writer);
			return writer.toByteArray();

		} catch (Exception|Error e) {
			e.printStackTrace();
		}
		return basicClass;
	} 




	private static void transformGuiNewChat(ClassNode node, boolean isObf) {
		final String SET_CHAT_LINE = isObf? "a" : "setChatLine";
		final String DESCRIPTOR = isObf? "(Lhh;IIZ)V" : "(Lnet/minecraft/util/text/ITextComponent;IIZ)V";
		/*
		 * 	INVOKEINTERFACE List.size() : int
    		BIPUSH 100
    		IF_ICMPLE L13

    		and

    		INVOKEINTERFACE List.size() : int
    		BIPUSH 100
    		IF_ICMPLE L15

    		This is where the constant 100 is loaded when the line counts are checked
		 * */
		for(MethodNode method : node.methods) {
			if(method.name.equals(SET_CHAT_LINE) && method.desc.equals(DESCRIPTOR)) {
				AbstractInsnNode target = null;
				int maxOps = 2;
				for (AbstractInsnNode instr : method.instructions.toArray()) {
					if(instr.getOpcode() == BIPUSH) {
						IntInsnNode intInstruct = (IntInsnNode) instr;
						if (intInstruct.operand == 100 && intInstruct.getNext().getOpcode() == IF_ICMPLE) {
							target = instr;
							maxOps--;

							MethodInsnNode myMethod = new MethodInsnNode(
									INVOKESTATIC,
									"com/theincgi/advancedMacros/asm/ChatLinesEditThingAndOthers", //class name
									"getMaxLineCount",  //method name
									"()I",  //no args, returns int
									false); //not interface
							method.instructions.insertBefore(target, myMethod);
							method.instructions.remove(target);
							if(maxOps==0) { System.out.println("AdvancedMacros: max chat lines is now editable");break; }
						}
					}
				}

			}
		}
	}


	public static int getMaxLineCount() {
		try {
			return Utils.tableFromProp(Settings.settings, "chat.maxLines", LuaValue.valueOf(100)).checkint();
		}catch (Exception | Error e) {
			return 100;
		}
	}

	private static void transformGuiScreenComponentClick(ClassNode node, boolean isObf){
		final String HANDLE_COMPONENT_CLICK = isObf? "a" : "handleComponentClick";
		final String DESCRIPTOR = isObf? "(Lhh;)Z" : "(Lnet/minecraft/util/text/ITextComponent;)Z";
		/*
 	 IF_ACMPNE L33   -- EDIT to new Label
   L34
    LINENUMBER 440 L34
    ALOAD 0: this
    ALOAD 2: clickevent
    INVOKEVIRTUAL ClickEvent.getValue() : String
    ICONST_0
    INVOKEVIRTUAL GuiScreen.sendChatMessage(String, boolean) : void
    GOTO L24
   L33 --go here if added if is false
    LINENUMBER 444 L33
   FRAME SAME
    GETSTATIC GuiScreen.LOGGER : Logger
    LDC "Don't know how to handle {}"
    ALOAD 2: clickevent
    INVOKEINTERFACE Logger.error(String, Object) : void
   L24
    LINENUMBER 447 L24
   FRAME SAME
    ICONST_1
    IRETURN
   L9*/
		
		for(MethodNode method : node.methods) {
			if(method.name.equals(HANDLE_COMPONENT_CLICK) && method.desc.equals(DESCRIPTOR)) {
				System.out.println("Found method");
				AbstractInsnNode target = null;
				LabelNode l24, l33;
				for( AbstractInsnNode instr : method.instructions.toArray() ) {
					if(instr.getOpcode() == Opcodes.LDC) {
						target = instr.getNext();
						if(target.getOpcode() == Opcodes.ALOAD) {
							target = instr.getNext().getNext();
							if(target.getOpcode() == Opcodes.INVOKEINTERFACE) {
								target = target.getNext();	
								if(target instanceof LabelNode) {
									l24 = (LabelNode) target;
									while( !(target instanceof LabelNode)) {
										target = target.getPrevious();
									}
									l33 = (LabelNode) target;
									int type = -2;
									while(type!=Opcodes.IF_ACMPNE) {
										target = target.getPrevious();
										type = target.getOpcode();
									}
									System.out.println(target.getClass());
									if(target instanceof JumpInsnNode) {
//										JumpInsnNode jsn = (JumpInsnNode) target;
										LabelNode l34 = (LabelNode) target.getPrevious().getPrevious().getPrevious().getPrevious().getPrevious().getPrevious();
										LabelNode newLabel = new LabelNode();
										InsnList list = new InsnList();
//										LabelNode oldTarget = jsn.label;
//										jsn.label = newLabel;
										MethodInsnNode check = new MethodInsnNode(
												INVOKESTATIC,
												"com/theincgi/advancedMacros/asm/ChatLinesEditThingAndOthers", //class name
												"isLuaTextComponent",  //method name
												"(Ljava/lang/Object;)Z",  //obj, returns bool
												false); //not interface
										MethodInsnNode call = new MethodInsnNode(
												INVOKESTATIC,
												"com/theincgi/advancedMacros/asm/ChatLinesEditThingAndOthers", //class name
												"clickLuaTextComponent",  //method name
												"(Ljava/lang/Object;)V",  //obj, returns void
												false); //not interface
										//call static check for clickEvent class
										//if ne jump to old label
										//otherwise, call clickEvent code
										
//										list.add(newLabel);
										list.add(new VarInsnNode(Opcodes.ALOAD, 2));
										list.add(check);
										list.add(new JumpInsnNode(Opcodes.IFEQ, newLabel));
										list.add(new VarInsnNode(Opcodes.ALOAD, 2));
										list.add(call);
										list.add(new JumpInsnNode(Opcodes.GOTO, l24));
										list.add(newLabel);
										
										method.instructions.insert(l34, list);
										System.out.println("GuiScreen has been modified for Lua Text components");
									}
								}
							}	
						}
					}
				}
				return;
			}
		}
		System.out.println(node.methods);
		
		/*
		 *  else if (clickevent.getAction() == ClickEvent.Action.RUN_COMMAND) {
         *       this.sendChatMessage(clickevent.getValue(), false);
         *  ***************EDIT**************
         *  }else{
         *       LOGGER.error("Don't know how to handle {}", (Object)clickevent);
         *  }
		 * 
		 * Adding case for if clickevent extends custom class then call custom action handler
		 * Using a method here to check if it matches to minimize the added byte code
		 * */
	}
	
	public static boolean isLuaTextComponent(Object obj) {
		return obj instanceof LuaTextComponentClickEvent;
	}
	public static void clickLuaTextComponent(Object ltc) {
		((LuaTextComponentClickEvent) ltc).click();
	}
	//TESTING CODE
//	private static void transformLocalChannelWrite(ClassNode node, boolean isObf) {
//		if(isObf) return;
//		//		final String INVOKE_CHAN_READ = isObf? null : "invokeChannelRead";
//		//		final String DESC = isObf? null : "(Lio/netty/channel/AbstractChannelHandlerContext;Ljava/lang/Object;)V";
//		final String INVOKE_CHAN_READ = isObf? null : "doWrite";
//		final String DESC = isObf? null : "(Lio/netty/channel/ChannelOutboundBuffer;)V";
//		for(MethodNode method : node.methods) {
//			//System.out.println(method.name + " " + method.desc);
//			if(method.name.equals(INVOKE_CHAN_READ) && method.desc.equals(DESC)) {
//				//System.out.println("Located invokeChannel Read");
//				//aload 2 for getting m
//				System.out.println("doWrite located");
//
//				for(AbstractInsnNode instr : method.instructions.toArray()) {
//					if(instr.getOpcode()==ASTORE) {
//						VarInsnNode astore = (VarInsnNode) instr;
//						if(astore.var==3) { //use to be 2
//
//							MethodInsnNode myMethod = new MethodInsnNode(
//									INVOKESTATIC,
//									"com/theincgi/advancedMacros/asm/ChatLinesEditThingAndOthers", //class name
//									"getPacketHackySend",  //method name
//									"(Ljava/lang/Object;)V",  //no args, returns int
//									false); //not interface
//							VarInsnNode loadM = new VarInsnNode(ALOAD, 3); //use to be 2
//							InsnList list = new InsnList();
//							list.add(loadM);
//							list.add(myMethod);
//
//							method.instructions.insert(instr, list);
//
//							System.out.println("registered hook in " + INVOKE_CHAN_READ + " of " + editedClasses[1]);
//							return;
//						}
//					}
//				}
//
//
//			}
//		}
//	}

//	static Queue<Object> recent = new LinkedList<>();
//	public static void getPacketHackySend(Object packet) {
//		if(packet==null) return;
//		try {
//			String name = packet.getClass().getName();
//			if(name.contains("Teleport") || name.contains("HeadLook") || name.contains("Velocity") ||
//					name.contains("RelMove") || name.contains("Metadata") || name.contains("LookMove") ||
//					name.contains("Position") || name.contains("TimeUpdate") || name.contains("EntityLook") || name.contains("SpawnObject") ||
//					name.contains("SpawnMob") || name.contains("EntityProperties") || name.contains("DestroyEntities") || name.contains("EntityStatus") ||
//					name.contains("KeepAlive") || name.contains("SPacketEffect") || name.contains("EntityEquipment") || name.contains("PlayerListItem") || name.contains("Rotation") || 
//					name.contains("SPacketBlockAction") || name.contains("Sound") || name.contains("ConfirmTrans") || name.contains("WindowProp")) return;
//			
//			if(recent.contains(packet)) return;
//			recent.offer(packet);
//			if(recent.size()>30) recent.poll();
//			switch (name) {
//			case "net.minecraft.network.play.client.CPacketClickWindow":{
//				CPacketClickWindow c = (CPacketClickWindow) packet;
//				System.out.printf("ClickWindow: Act#: %d, type: %s, **id: %d**, wID: %d, button: %d\n", c.getActionNumber(), c.getClickType().name(), c.getSlotId(), c.getWindowId(), c.getUsedButton());
//				break;
//			}
//			default:
//				System.out.println(name);
//			}
//		}catch (Throwable e) {
//			// TODO: handle exception
//		}
//	}
}
