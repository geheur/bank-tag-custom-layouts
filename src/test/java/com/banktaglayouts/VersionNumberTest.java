package com.banktaglayouts;

import org.junit.Assert;
import org.junit.Test;

public class VersionNumberTest
{

	@Test
	public void testVersionNumbers() {
		VersionNumber v1 = new VersionNumber(1, 4, 10);
		VersionNumber v2 = new VersionNumber(1, 4, 11);
		VersionNumber v1s = new VersionNumber("1.4.10");
		VersionNumber v2s = new VersionNumber("1.4.11");

//		com.jogamp.common.util.VersionNumber jv1 = new com.jogamp.common.util.VersionNumber(1, 4, 10);
//		com.jogamp.common.util.VersionNumber jv2 = new com.jogamp.common.util.VersionNumber(1, 4, 11);
//		com.jogamp.common.util.VersionNumber jv1s = new com.jogamp.common.util.VersionNumber("1.4.10");
//		com.jogamp.common.util.VersionNumber jv2s = new com.jogamp.common.util.VersionNumber("1.4.11");

		Assert.assertEquals(0, v1.compareTo(v1s));
		Assert.assertEquals(0, v2.compareTo(v2s));
		Assert.assertEquals(-1, v1.compareTo(v2s));
		Assert.assertEquals(1, v2.compareTo(v1s));

//		Assert.assertEquals(0, jv1.compareTo(jv1s));
//		Assert.assertEquals(0, jv2.compareTo(jv2s));
//		Assert.assertEquals(-1, jv1.compareTo(jv2s));
//		Assert.assertEquals(1, jv2.compareTo(jv1s));
	}
}
