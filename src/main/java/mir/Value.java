//泛值类，方便管理不同的代码结构

package mir;

import mir.type.SymType;
import tools.MyListNode;

import java.util.ArrayList;
import java.util.Objects;

public class Value extends MyListNode implements Cloneable {
    public static final String GLOBAL_PRE = "@";
    public static final String LOCAL_PRE = "%";
    public static final String GLOBAL_NAME_PRE = "g";
    public static final String LOCAL_NAME_PRE = "l";
    public static final String FPARAM_NAME_PRE = "f";
    public static int valueNum = 0;
    protected Tag tag = Tag.value;
    protected String pre;
    protected String name;
    protected SymType type;
    private int hashId = valueNum++;
    private ArrayList<Instr> users = new ArrayList<>();

    public Value() {
        super();
    }

    public Value(SymType type) {
        super();
        this.type = type;
    }

    public Object clone() throws CloneNotSupportedException {
        Value v = (Value) super.clone();
        v.users = new ArrayList<>(users);
        return v;
    }

    public Tag getTag() {
        return tag;
    }

    public int hashCode() {
        return Objects.hash(hashId);
    }

    public void setPre(String pre) {
        this.pre = pre;
    }

    public String getNameWithoutPre() {
        return this.name;
    }

    public String getName() {
        return pre + name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public SymType getType() {
        return type;
    }

    /**
     * 这个函数依据def-use关系将所用用到this的地方替换为新的Value
     */
    public void replaceAllUsesWith(Value v) {
        for (Instr user : users) {
            for (int i = 0; i < user.getUsedValues().size(); i++) {
                if (user.getUsedValues().get(i).equals(this)) {
                    user.getUsedValues().set(i, v);
                    if (!v.getUsers().contains(user)) {
                        v.addUser(user);
                    }
                }
            }
        }
        users.clear();
    }

    public ArrayList<Instr> getUsers() {
        return users;
    }

    public void addUser(Instr instr) {
        if (!users.isEmpty()) {
            for (Instr instr1 : users) {
                if (instr1.equals(instr)) {
                    return;
                }
            }
        }
        users.add(instr);
    }

    public boolean isBincal() {
        return this.tag == Tag.bincal;
    }

    public boolean isStore() {
        return this.tag == Tag.store;
    }

    public boolean isLoad() {
        return this.tag == Tag.load;
    }

    public boolean isAlloca() {
        return this.tag == Tag.alloca;
    }

    public boolean isGep() {
        return this.tag == Tag.gep;
    }

    public boolean isCall() {
        return this.tag == Tag.call;
    }

    public boolean isIcmp() {
        return this.tag == Tag.icmp;
    }

    public boolean isFcmp() {
        return this.tag == Tag.fcmp;
    }

    public boolean isPhi() {
        return this.tag == Tag.phi;
    }

    public boolean isBranch() {
        return this.tag == Tag.branch;
    }

    public boolean isJump() {
        return this.tag == Tag.jump;
    }

    public boolean isRet() {
        return this.tag == Tag.ret;
    }

    public boolean isFptosi() {
        return this.tag == Tag.fptosi;
    }

    public boolean isSitofp() {
        return this.tag == Tag.sitofp;
    }

    public boolean isBitcast() {
        return this.tag == Tag.bitcast;
    }

    public boolean isZext() {
        return this.tag == Tag.zext;
    }

    public boolean isFunc() {
        return this.tag == Tag.func;
    }

    public boolean isBb() {
        return this.tag == Tag.bb;
    }

    public boolean isAshr() {
        return this.tag == Tag.ashr;
    }

    public boolean islshr() {
        return this.tag == Tag.lshr;
    }

    public boolean isShl() {
        return this.tag == Tag.shl;
    }

    public boolean isFneg() {
        return this.tag == Tag.fneg;
    }

    public boolean isMove() {
        return this.tag == Tag.move;
    }

    public boolean equals(Object o) {
        if (o == this) {
            return true;
        }
        if (o == null || o.getClass() != getClass()) {
            return false;
        }
        return hashId == ((Value) o).hashId;
    }

    public enum Tag {
        value,      //泛值 ： Anything
        bincal,     //二元运算
        store,      //存储
        load,       //加载
        alloca,     //分配空间
        gep,        //get element's pointer
        call,       //调用函数
        icmp,       //integer compare
        fcmp,       //float compare
        fneg,       //浮点数或浮点向量的负
        phi,        //多目条件赋值
        //%value = phi i32 [66, %branch1], [77, %branch2], [88, %branch3]
        //如果前一步执行的是branch1分支，则返回值为66；当执行的是branch2，则返回值为77；以此类推…
        branch,     //条件跳转
        jump,       //直接跳转
        ret,        //返回
        fptosi,     //float强转为int
        sitofp,     //int强转为float
        bitcast,    //数位转化
        zext,       //数位扩展 bool --> int
        trunc,      //数位截断
        func,       //函数
        bb,         //basic block

        //移位类乘除法优化可能使用
        ashr,       //算数右移
        lshr,       //逻辑右移
        shl,        //左移
        move,       //寄存器间赋值
    }

}
