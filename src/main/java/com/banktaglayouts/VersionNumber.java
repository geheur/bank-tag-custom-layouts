package com.banktaglayouts;

class VersionNumber implements Comparable<Object>
{

	final int n1;
	final int n2;
	final int n3;

	public VersionNumber(String versionString)
	{
		if (versionString == null || versionString.isEmpty())
		{
			this.n1 = 0;
			this.n2 = 0;
			this.n3 = 0;
		}
		else
		{
			String[] split = versionString.split("\\.");
			this.n1 = Integer.parseInt(split[0]);
			this.n2 = Integer.parseInt(split[1]);
			this.n3 = Integer.parseInt(split[2]);
		}
	}

	public VersionNumber(int n1, int n2, int n3)
	{
		this.n1 = n1;
		this.n2 = n2;
		this.n3 = n3;
	}

	@Override
	public int compareTo(Object obj)
	{
		VersionNumber o = (VersionNumber) obj;
		if (this.n1 > o.n1)
		{
			return 1;
		}
		if (this.n1 < o.n1)
		{
			return -1;
		}
		if (this.n2 > o.n2)
		{
			return 1;
		}
		if (this.n2 < o.n2)
		{
			return -1;
		}
		if (this.n3 > o.n3)
		{
			return 1;
		}
		if (this.n3 < o.n3)
		{
			return -1;
		}
		return 0;
	}

	@Override
	public String toString() {
		return n1 + "." + n2 + "." + n3;
	}

}
