/**
 * vxmlTemplates — Pre-built VoiceXML 2.1 Standard Scenario Templates
 */
export const VXML_TEMPLATES: Record<string, { title: string; description: string; vxml: string }> = {
  restaurant: {
    title: 'Restaurant Booking (VXML 2.1)',
    description: 'Standard VoiceXML scenario for table reservation and hours inquiry',
    vxml: `<?xml version="1.0" encoding="UTF-8"?>
<vxml version="2.1" xmlns="http://www.w3.org/2001/vxml">
  <meta name="title" content="Restaurant Booking System"/>
  
  <form id="welcome">
    <block>
      <prompt>Welcome to Bella Italia Restaurant IVR.</prompt>
      <goto next="#main_menu"/>
    </block>
  </form>

  <menu id="main_menu">
    <prompt>Press 1 for reservations. Press 2 for opening hours. Press 0 to talk to staff.</prompt>
    <choice dtmf="1" next="#reservations"/>
    <choice dtmf="2" next="#hours"/>
    <choice dtmf="0" next="#transfer_staff"/>
  </menu>

  <form id="reservations">
    <field name="party_size">
      <prompt>Please enter the number of guests followed by hash.</prompt>
      <grammar mode="dtmf" type="application/srgs+xml">
        <rule id="size" scope="public"><one-of><item>1</item><item>2</item><item>3</item><item>4</item><item>5</item><item>6</item></one-of></rule>
      </grammar>
      <filled>
        <log expr="'Party size: ' + party_size"/>
        <goto next="#confirm_booking"/>
      </filled>
    </field>
  </form>

  <form id="hours">
    <block>
      <prompt>We are open daily from 11 AM to 10 PM. Thank you.</prompt>
      <disconnect/>
    </block>
  </form>

  <form id="transfer_staff">
    <transfer name="trans" dest="SIP/101" bridge="true">
      <prompt>Transferring your call to our receptionist...</prompt>
    </transfer>
  </form>

  <form id="confirm_booking">
    <block>
      <prompt>Your reservation request has been received. Thank you!</prompt>
      <disconnect/>
    </block>
  </form>
</vxml>`
  },

  banking: {
    title: 'Banking Self-Service (VXML 2.1)',
    description: 'Account balance query and card services VoiceXML flow',
    vxml: `<?xml version="1.0" encoding="UTF-8"?>
<vxml version="2.1" xmlns="http://www.w3.org/2001/vxml">
  <form id="auth">
    <field name="account_num">
      <prompt>Welcome to Nexus Bank. Please enter your 4-digit account PIN.</prompt>
      <grammar mode="dtmf" type="application/srgs+xml">
        <rule id="pin" scope="public"><one-of><item>1</item><item>2</item><item>3</item><item>4</item><item>5</item><item>6</item><item>7</item><item>8</item><item>9</item><item>0</item></one-of></rule>
      </grammar>
      <filled>
        <goto next="#bank_menu"/>
      </filled>
    </field>
  </form>

  <menu id="bank_menu">
    <prompt>Press 1 for balance inquiry. Press 2 to report a lost card.</prompt>
    <choice dtmf="1" next="#balance"/>
    <choice dtmf="2" next="#lost_card"/>
  </menu>

  <form id="balance">
    <block>
      <prompt>Your current available balance is 1,250 dollars.</prompt>
      <disconnect/>
    </block>
  </form>

  <form id="lost_card">
    <transfer name="fraud_dept" dest="SIP/999" bridge="true">
      <prompt>Connecting to emergency card services immediately.</prompt>
    </transfer>
  </form>
</vxml>`
  }
}
