package com.bencodez.advancedcore.tests.inventory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

import com.bencodez.advancedcore.api.inventory.BInventory.ClickEvent;
import com.bencodez.advancedcore.api.inventory.editgui.valuetypes.EditGUIValue;
import com.bencodez.advancedcore.api.inventory.editgui.valuetypes.EditGUIValueList;
import com.bencodez.simpleapi.valuerequest.InputMethod;

public class EditGUIValueBehaviorTest {

	@Test
	public void commonValueNormalizationHandlesNullsAndStringNumbers() {
		TestValue value = new TestValue("Example", null);

		assertEquals("fallback", value.stringValue("fallback"));
		assertEquals(Integer.valueOf(5), value.numberValue(Integer.valueOf(5)));

		value.setCurrentValue("12.5");
		assertEquals(12.5D, value.numberValue(Integer.valueOf(0)).doubleValue());

		value.setCurrentValue("not-a-number");
		assertEquals(Integer.valueOf(7), value.numberValue(Integer.valueOf(7)));
	}

	@Test
	public void stringListNormalizationAcceptsAnyCollectionAndIgnoresNulls() {
		TestValue value = new TestValue("List", List.of());
		ArrayList<Object> input = new ArrayList<>();
		input.add("one");
		input.add(Integer.valueOf(2));
		input.add(null);
		value.setCurrentValue(input);

		assertEquals(List.of("one", "2"), value.stringListValue());
	}

	@Test
	public void applyValueUpdatesCurrentValueAndInvokesPersistenceHook() {
		TestValue value = new TestValue("Example", "old");
		AtomicReference<String> saved = new AtomicReference<>();

		value.apply(null, "new", saved::set);

		assertEquals("new", saved.get());
		assertEquals("new", value.getCurrentValue());
	}

	@Test
	public void nullInputMethodFallsBackToDialog() {
		TestValue value = new TestValue("Example", null);
		value.inputMethod(null);
		assertEquals(InputMethod.DIALOG, value.getInputMethod());
	}

	@Test
	public void defaultAndListDisplayMetadataAreCentralized() {
		TestValue value = new TestValue("Example", "value");
		assertEquals("&cSet test for Example", value.getButtonName());
		assertEquals(List.of("&cCurrent value: value"), value.getButtonLore());

		EditGUIValueList list = new EditGUIValueList("Entries", new ArrayList<>(List.of("a", "b"))) {
			@Override
			public void setValue(Player player, ArrayList<String> value) {
			}
		};
		assertEquals("&cEdit list for Entries", list.getButtonName());
		assertEquals(List.of("a", "b"), list.getButtonLore());

		EditGUIValueList nullList = new EditGUIValueList("Entries", null) {
			@Override
			public void setValue(Player player, ArrayList<String> value) {
			}
		};
		assertEquals(List.of("&cCurrent value: null"), nullList.getButtonLore());
	}

	@Test
	public void containsKeyHandlesMissingEditData() {
		TestValue value = new TestValue("Example", null);
		assertFalse(value.containsKey(null));
	}

	private static final class TestValue extends EditGUIValue {
		private TestValue(String key, Object value) {
			super(key, value);
		}

		@Override
		public String getType() {
			return "test";
		}

		@Override
		public void onClick(ClickEvent event) {
		}

		private String stringValue(String fallback) {
			return currentString(fallback);
		}

		private Number numberValue(Number fallback) {
			return currentNumber(fallback);
		}

		private ArrayList<String> stringListValue() {
			return currentStringList();
		}

		private <T> void apply(Player player, T value, java.util.function.Consumer<T> consumer) {
			applyValue(player, value, consumer);
		}
	}
}
